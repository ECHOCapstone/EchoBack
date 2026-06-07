package com.capstoneecho.echo_back.external.llm.canonical;

import com.capstoneecho.echo_back.external.llm.GeminiCallExecutor;
import com.capstoneecho.echo_back.external.llm.PromptCatalog;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Gemini 의 구조화 출력으로 단어별 ARPABET 음소를 받는다. 인벤토리 외 토큰이 섞이면 1회 재시도하고,
// 또 실패하면 BusinessException(CANONICAL_GENERATION_FAILED) 으로 변환해 사용자에게 명시적 에러를 노출한다.
// silent 폴백 (g2p 옛 경로로 복귀 등) 은 의도적으로 차단한다 — 그게 오개념 누설의 근본 원인이었다.
@Component
public class GeminiCanonicalGenerator implements LlmCanonicalGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiCanonicalGenerator.class);
    private static final String PROMPT_KEY = "canonical-generation";

    private final GeminiCallExecutor executor;
    private final PromptCatalog prompts;
    private final PhonemeInventory inventory;

    public GeminiCanonicalGenerator(
            GeminiCallExecutor executor,
            PromptCatalog prompts,
            PhonemeInventory inventory) {
        this.executor = executor;
        this.prompts = prompts;
        this.inventory = inventory;
    }

    @Override
    public CanonicalResult generate(String text) {
        if (text == null || text.isBlank()) {
            return new CanonicalResult(List.of());
        }
        if (!executor.isAvailable()) {
            // Gemini API 키 미설정. canonical 은 LLM 으로만 만든다 — g2p 폴백 금지.
            log.warn("Gemini 미설정 상태에서 canonical 요청 — CANONICAL_GENERATION_FAILED");
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "Gemini provider 가 설정되지 않았습니다");
        }

        CanonicalResult first = callOnce(text);
        List<String> firstUnknown = unknownTokens(first);
        if (firstUnknown.isEmpty()) {
            return first;
        }
        log.warn("Gemini canonical 1차 응답에 비표준 음소 포함 unknown={} — 재시도", firstUnknown);

        CanonicalResult second = callOnce(text);
        List<String> secondUnknown = unknownTokens(second);
        if (secondUnknown.isEmpty()) {
            return second;
        }
        log.error("Gemini canonical 재시도에도 비표준 음소 포함 unknown={} input={}", secondUnknown, text);
        throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED,
                "canonical 응답에 인벤토리 외 음소가 포함됨: " + secondUnknown);
    }

    private CanonicalResult callOnce(String text) {
        String userPrompt = prompts.render(PROMPT_KEY, Map.of(
                "text", text,
                "inventory", inventory.promptListing()));
        try {
            CanonicalResult result = executor.callRequired(
                    prompts.raw("system"),
                    userPrompt,
                    CanonicalSchema.canonical(),
                    CanonicalResult.class);
            if (result == null) {
                throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "Gemini 응답 본문이 비었습니다");
            }
            return result;
        } catch (GeminiCallExecutor.GeminiCallException ex) {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, ex.getMessage());
        }
    }

    // 응답 시퀀스 안의 인벤토리 외 음소 토큰을 모은다. 빈 리스트면 통과.
    private List<String> unknownTokens(CanonicalResult result) {
        if (result == null || result.words() == null) {
            return List.of();
        }
        return result.words().stream()
                .flatMap(w -> w.phonemes() == null ? java.util.stream.Stream.<String>empty() : w.phonemes().stream())
                .filter(p -> p != null && !p.isBlank())
                .filter(p -> !inventory.contains(p))
                .map(p -> p.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}

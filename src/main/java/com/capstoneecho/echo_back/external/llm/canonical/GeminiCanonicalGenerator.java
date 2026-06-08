package com.capstoneecho.echo_back.external.llm.canonical;

import com.capstoneecho.echo_back.external.llm.GeminiCallExecutor;
import com.capstoneecho.echo_back.external.llm.PromptCatalog;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// CMU 기반 baseline + Gemini refine 하이브리드 canonical 생성기.
//   1단계 — ModelServerClient.g2p(text) 가 결정적 CMU baseline 을 돌려준다.
//   2단계 — Gemini refine 호출이 baseline 을 문맥 의존 발음 / 학습자 연결 발음에 맞게 보정해 최종 canonical 을 만든다.
// 인벤토리 외 토큰이 LLM 응답에 섞이면 1회 재시도, 그래도 있으면 BusinessException(CANONICAL_GENERATION_FAILED).
// silent fallback 금지 — 모델 서버 /g2p 실패도, Gemini 실패도 사용자에게 명시적 에러로 노출된다.
@Component
public class GeminiCanonicalGenerator implements LlmCanonicalGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiCanonicalGenerator.class);
    private static final String PROMPT_KEY = "canonical-generation";

    private final GeminiCallExecutor executor;
    private final PromptCatalog prompts;
    private final PhonemeInventory inventory;
    private final ModelServerClient modelServerClient;

    public GeminiCanonicalGenerator(
            GeminiCallExecutor executor,
            PromptCatalog prompts,
            PhonemeInventory inventory,
            ModelServerClient modelServerClient) {
        this.executor = executor;
        this.prompts = prompts;
        this.inventory = inventory;
        this.modelServerClient = modelServerClient;
    }

    @Override
    public CanonicalResult generate(String text, List<String> perceived) {
        if (text == null || text.isBlank()) {
            return new CanonicalResult(List.of());
        }
        if (!executor.isAvailable()) {
            log.warn("Gemini 미설정 상태에서 canonical 요청 — CANONICAL_GENERATION_FAILED");
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "Gemini provider 가 설정되지 않았습니다");
        }

        List<CanonicalWord> baseline = modelServerClient.g2p(text);

        CanonicalResult first = refine(text, baseline, perceived);
        List<String> firstUnknown = unknownTokens(first);
        if (firstUnknown.isEmpty()) {
            return first;
        }
        log.warn("Gemini canonical refine 1차 응답에 비표준 음소 포함 unknown={} — 재시도", firstUnknown);

        CanonicalResult second = refine(text, baseline, perceived);
        List<String> secondUnknown = unknownTokens(second);
        if (secondUnknown.isEmpty()) {
            return second;
        }
        log.error("Gemini canonical refine 재시도에도 비표준 음소 포함 unknown={} input={}", secondUnknown, text);
        throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED,
                "canonical 응답에 인벤토리 외 음소가 포함됨: " + secondUnknown);
    }

    private CanonicalResult refine(String text, List<CanonicalWord> baseline, List<String> perceived) {
        String userPrompt = prompts.render(PROMPT_KEY, Map.of(
                "text", text,
                "baseline", renderBaseline(baseline),
                "perceivedSection", renderPerceivedSection(perceived),
                "inventory", inventory.markdownTable()));
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

    // baseline 을 프롬프트 본문에 끼울 텍스트로 직렬화한다. 단어 → 음소 줄로 나열.
    private static String renderBaseline(List<CanonicalWord> baseline) {
        if (baseline == null || baseline.isEmpty()) {
            return "(없음)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (CanonicalWord w : baseline) {
            sb.append(String.format("  [%d] %s — %s%n",
                    i++,
                    (w.word() == null || w.word().isBlank()) ? "-" : w.word(),
                    String.join(" ", w.phonemes())));
        }
        return sb.toString().stripTrailing();
    }

    // perceived 가 있을 때만 학습자 발음 섹션을 채운다. 비면 빈 문자열 — PromptCatalog 가 placeholder 정리.
    private static String renderPerceivedSection(List<String> perceived) {
        if (perceived == null || perceived.isEmpty()) {
            return "";
        }
        return "학습자가 실제로 발음한 음소: " + String.join(" ", perceived);
    }

    // LLM refine 응답 안의 인벤토리 외 음소 토큰. 빈 리스트면 통과.
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

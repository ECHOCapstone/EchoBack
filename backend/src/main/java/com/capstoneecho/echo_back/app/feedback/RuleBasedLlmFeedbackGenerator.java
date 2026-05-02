package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 외부 LLM 호출 없이 규칙 기반으로 한국어 가이드 문장만 생성한다.
// 재연습 단어 결정은 PracticeWordResolver 가 담당하므로 본 구현은 음소별 코칭 메시지에만 집중한다.
//
// app.llm.provider 가 rule-based(default) 일 때 활성화되며, gemini 일 때는 등록되지 않는다.
@Component
@ConditionalOnProperty(
        prefix = "app.llm",
        name = "provider",
        havingValue = "rule-based",
        matchIfMissing = true
)
class RuleBasedLlmFeedbackGenerator implements LlmFeedbackGenerator {

    private final double stepPassThreshold;
    private final double stepOkThreshold;

    RuleBasedLlmFeedbackGenerator(AppProperties properties) {
        var threshold = properties.feedback().stepThreshold();
        this.stepPassThreshold = threshold.pass();
        this.stepOkThreshold = threshold.ok();
    }

    // 음소 → 한국어 라벨 + 코칭 한 줄. 키는 ARPAbet 소문자 표기.
    private static final Map<String, Hint> HINTS = Map.ofEntries(
            Map.entry("r", new Hint("R", "혀 끝을 입천장에 닿지 않게 살짝 말아 올리는 것이 중요해요.")),
            Map.entry("l", new Hint("L", "혀 끝을 윗니 뒤쪽 잇몸에 가볍게 대고 발음해 보세요.")),
            Map.entry("v", new Hint("V", "윗니로 아랫입술을 살짝 물고 진동을 주며 발음하세요.")),
            Map.entry("b", new Hint("B", "양 입술을 붙였다가 떼며 강하게 터뜨려 발음해 보세요.")),
            Map.entry("f", new Hint("F", "윗니로 아랫입술을 살짝 물고 바람만 흘려보내세요.")),
            Map.entry("p", new Hint("P", "두 입술을 붙였다가 짧게 터뜨리며 바람을 내보내세요.")),
            Map.entry("th", new Hint("TH", "혀 끝을 윗니 사이에 살짝 끼우고 바람을 흘려보내세요.")),
            Map.entry("dh", new Hint("DH", "혀 끝을 윗니에 살짝 대고 성대를 울리며 발음해 보세요.")),
            Map.entry("sh", new Hint("SH", "혀를 입천장 가까이 둔 채 'ㅅ' 보다 부드럽게 흘려보내세요.")),
            Map.entry("zh", new Hint("ZH", "혀를 입천장에 가까이 두고 성대를 울리며 발음해 보세요.")),
            Map.entry("ng", new Hint("NG", "혀 뒷부분을 입천장에 붙이고 코로 소리를 흘려보내세요."))
    );

    @Override
    public String stepGuidance(
            String targetText,
            double stepScore,
            List<String> perceived,
            List<String> canonical,
            List<PhonemeErrorResponse> errors
    ) {
        var label = targetText != null && !targetText.isBlank() ? "\"" + targetText + "\"" : "이번";
        if (stepScore >= stepPassThreshold) {
            return label + " 발음이 정확해요. 다음으로 넘어가 볼까요?";
        }
        var hint = lookup(pickWeakest(errors));
        if (stepScore >= stepOkThreshold) {
            if (hint != null) {
                return label + " 발음 좋아요. " + hint.label() + " 부분만 조금 더 또렷하게 해보세요.";
            }
            return label + " 발음 좋아요. 한 번 더 또렷하게 시도해 봐요.";
        }
        if (hint != null) {
            return hint.label() + " 발음이 부족해요. " + hint.guidance() + " 다시 한 번 도전해 봐요.";
        }
        return label + " 발음을 다시 한 번 또렷하게 시도해 봐요.";
    }

    @Override
    public String unitGuidance(
            String unitTitle,
            double accuracy,
            String weakPhoneme,
            List<PhonemeErrorResponse> errors
    ) {
        // 정확도 수치는 프론트가 별도 라벨로 표시하므로 이 가이드 문장에는 포함하지 않는다.
        // "약점 음소 + 한 줄 코칭 + 재연습 안내" 만 담아 정확도 중복 노출을 막는다.
        var hint = lookup(weakPhoneme);
        var sb = new StringBuilder();
        if (hint != null) {
            sb.append(hint.label()).append(" 발음이 가장 많이 틀렸어요. ");
            sb.append(hint.guidance()).append(" ");
        } else if (weakPhoneme != null && !weakPhoneme.isBlank()) {
            sb.append(weakPhoneme).append(" 음소에서 오류가 가장 많았어요. ");
        } else {
            sb.append("전반적으로 안정적인 발음이지만 더 연습하면 좋아요. ");
        }
        sb.append("아래 단어로 다시 한 번 연습해 보세요.");
        return sb.toString();
    }

    @Override
    public String retryGuidance(
            String practiceWord,
            boolean correct,
            List<String> perceived,
            List<String> canonical
    ) {
        if (correct) {
            return practiceWord + " 발음이 정확해졌어요. 다음 단계로 넘어가도 좋아요.";
        }
        return practiceWord + " 의 발음을 한 번 더 또렷하게 굴려 보세요. 입 모양과 혀 위치에 집중해 주세요.";
    }

    private String pickWeakest(List<PhonemeErrorResponse> errors) {
        if (errors == null || errors.isEmpty()) return null;
        return errors.stream()
                .map(e -> e.canonical() != null ? e.canonical() : e.perceived())
                .filter(p -> p != null)
                .findFirst()
                .orElse(null);
    }

    private Hint lookup(String weakPhoneme) {
        if (weakPhoneme == null || weakPhoneme.isBlank()) {
            return null;
        }
        return HINTS.get(weakPhoneme.toLowerCase());
    }

    private record Hint(String label, String guidance) {}
}

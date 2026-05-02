package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 외부 LLM 호출 없이 규칙 기반으로 한국어 가이드 문장과 재연습 단어를 생성하는 폴백 구현.
// app.llm.provider 가 rule-based(default) 일 때 활성화되며, gemini 일 때는 등록되지 않는다.
@Component
@ConditionalOnProperty(
        prefix = "app.llm",
        name = "provider",
        havingValue = "rule-based",
        matchIfMissing = true
)
class RuleBasedLlmFeedbackGenerator implements LlmFeedbackGenerator {

    private static final double STEP_PASS_THRESHOLD = 80.0;
    private static final double STEP_OK_THRESHOLD = 60.0;

    // 음소 → 한국어 설명 + 추천 재연습 단어. 키는 ARPAbet 소문자 표기.
    private static final Map<String, Hint> HINTS = Map.ofEntries(
            Map.entry("r", new Hint("R", "rabbit", "혀 끝을 입천장에 닿지 않게 살짝 말아 올리는 것이 중요해요.")),
            Map.entry("l", new Hint("L", "light", "혀 끝을 윗니 뒤쪽 잇몸에 가볍게 대고 발음해 보세요.")),
            Map.entry("v", new Hint("V", "vest", "윗니로 아랫입술을 살짝 물고 진동을 주며 발음하세요.")),
            Map.entry("b", new Hint("B", "best", "양 입술을 붙였다가 떼며 강하게 터뜨려 발음해 보세요.")),
            Map.entry("th", new Hint("TH", "think", "혀 끝을 윗니 사이에 살짝 끼우고 바람을 흘려보내세요.")),
            Map.entry("dh", new Hint("DH", "this", "혀 끝을 윗니에 살짝 대고 성대를 울리며 발음해 보세요.")),
            Map.entry("sh", new Hint("SH", "shoes", "혀를 입천장 가까이 둔 채 'ㅅ' 보다 부드럽게 흘려보내세요.")),
            Map.entry("zh", new Hint("ZH", "measure", "혀를 입천장에 가까이 두고 성대를 울리며 발음해 보세요.")),
            Map.entry("ng", new Hint("NG", "song", "혀 뒷부분을 입천장에 붙이고 코로 소리를 흘려보내세요."))
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
        if (stepScore >= STEP_PASS_THRESHOLD) {
            return label + " 발음이 정확해요. 다음으로 넘어가 볼까요?";
        }
        var hint = lookup(pickWeakest(errors));
        if (stepScore >= STEP_OK_THRESHOLD) {
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

    private String pickWeakest(List<PhonemeErrorResponse> errors) {
        if (errors == null || errors.isEmpty()) return null;
        return errors.stream()
                .map(e -> e.canonical() != null ? e.canonical() : e.perceived())
                .filter(p -> p != null)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Generated generate(
            String unitTitle,
            double accuracy,
            String weakPhoneme,
            List<PhonemeErrorResponse> errors
    ) {
        // 정확도 수치는 프론트가 별도 라벨로 표시하므로 이 가이드 문장에는 포함하지 않는다.
        // 가이드는 "약점 음소 + 한 줄 코칭 + 재연습 안내" 만 담아 정확도 중복 노출을 막는다.
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
        // weakPhoneme 매핑 실패 시 챕터 제목 기반 폴백을 사용해 "잰말놀이/V·B 등 챕터에서도 항상 rabbit"
        // 으로 떨어지는 문제를 막는다.
        var practiceWord = hint != null ? hint.practiceWord() : PracticeWordPolicy.byUnitTitle(unitTitle);
        return new Generated(sb.toString(), practiceWord);
    }

    @Override
    public Generated retryGuidance(
            String practiceWord,
            boolean correct,
            List<String> perceived,
            List<String> canonical
    ) {
        if (correct) {
            return new Generated(practiceWord + " 발음이 정확해졌어요. 다음 단계로 넘어가도 좋아요.", practiceWord);
        }
        return new Generated(
                practiceWord + " 의 발음을 한 번 더 또렷하게 굴려 보세요. 입 모양과 혀 위치에 집중해 주세요.",
                practiceWord
        );
    }

    private Hint lookup(String weakPhoneme) {
        if (weakPhoneme == null || weakPhoneme.isBlank()) {
            return null;
        }
        return HINTS.get(weakPhoneme.toLowerCase());
    }

    private record Hint(String label, String practiceWord, String guidance) {}
}

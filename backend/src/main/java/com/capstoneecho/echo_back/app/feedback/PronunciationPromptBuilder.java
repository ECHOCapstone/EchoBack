package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import org.springframework.stereotype.Component;

import java.util.List;

// LLM 에 보낼 발음 코칭 프롬프트를 만든다. 입력에 영문/음소가 섞여 있어도 출력은 한국어
// 한두 문장이 되도록 directive 로 묶어 둔다.
@Component
public class PronunciationPromptBuilder {

    private static final String STEP_DIRECTIVE =
            "너는 한국인 영어 학습자를 돕는 발음 코치다. 아래 학습 정보를 보고 한국어로 한 문장만 답해라. " +
                    "이모지 없이, 친근한 존대 어투로, 60자 이내.";
    private static final String UNIT_DIRECTIVE =
            "너는 한국인 영어 학습자를 돕는 발음 코치다. 학습 한 회차의 누적 결과를 받아 한국어로 답한다. " +
                    "정확도 점수는 화면에 별도로 표시되므로 본문에 다시 언급하지 마라. " +
                    "가장 자주 틀린 음소 한 가지를 짚고 다음 학습에 도움될 한 줄 코칭을 더해 두 문장 이내로 작성한다. " +
                    "이모지 없이, 친근한 존대 어투.";
    private static final String RETRY_DIRECTIVE =
            "너는 한국인 영어 학습자의 발음 코치다. 사용자가 권장 단어를 다시 발음한 결과를 보고 정/오를 판정하고 짧게 한국어로 안내한다. " +
                    "출력은 정확히 두 줄. " +
                    "첫 줄: PASS 또는 FAIL (영어 그대로). " +
                    "둘째 줄: 한국어 한 문장 (60자 이내, 이모지 없이). 정답이면 칭찬과 다음 단계 안내, 오답이면 발음 교정 힌트.";

    public String buildStepPrompt(
            String targetText,
            double stepScore,
            List<String> perceived,
            List<String> canonical,
            List<PhonemeErrorResponse> errors
    ) {
        var sb = new StringBuilder(STEP_DIRECTIVE).append("\n\n[학습 정보]\n");
        sb.append("- 목표 발음: ").append(safeText(targetText)).append('\n');
        sb.append("- 점수(0~100): ").append(formatScore(stepScore)).append('\n');
        sb.append("- 정답 음소: ").append(joinPhonemes(canonical)).append('\n');
        sb.append("- 인식 음소: ").append(joinPhonemes(perceived)).append('\n');
        sb.append("- 오류 음소: ").append(summarizeErrors(errors));
        return sb.toString();
    }

    public String buildUnitPrompt(
            String unitTitle,
            double accuracy,
            String weakPhoneme,
            List<PhonemeErrorResponse> errors
    ) {
        var sb = new StringBuilder(UNIT_DIRECTIVE).append("\n\n[누적 결과]\n");
        sb.append("- 학습 단원: ").append(safeText(unitTitle)).append('\n');
        sb.append("- 평균 정확도(0~100): ").append(formatScore(accuracy)).append('\n');
        sb.append("- 가장 자주 틀린 음소: ").append(safeText(weakPhoneme)).append('\n');
        sb.append("- 오류 목록: ").append(summarizeErrors(errors));
        return sb.toString();
    }

    public String buildRetryPrompt(
            String practiceWord,
            List<String> perceived,
            List<String> canonical
    ) {
        var sb = new StringBuilder(RETRY_DIRECTIVE).append("\n\n[재연습 결과]\n");
        sb.append("- 재연습 단어: ").append(safeText(practiceWord)).append('\n');
        sb.append("- 정답 음소: ").append(joinPhonemes(canonical)).append('\n');
        sb.append("- 인식 음소: ").append(joinPhonemes(perceived));
        return sb.toString();
    }

    private static String safeText(String value) {
        return value != null && !value.isBlank() ? value : "(없음)";
    }

    private static String joinPhonemes(List<String> list) {
        if (list == null || list.isEmpty()) return "(없음)";
        return String.join(" ", list);
    }

    private static String summarizeErrors(List<PhonemeErrorResponse> errors) {
        if (errors == null || errors.isEmpty()) return "(없음)";
        var sb = new StringBuilder();
        for (var e : errors) {
            if (sb.length() > 0) sb.append(", ");
            var canonical = e.canonical() != null ? e.canonical() : "-";
            var perceived = e.perceived() != null ? e.perceived() : "-";
            sb.append(e.op()).append('(').append(canonical).append('→').append(perceived).append(')');
        }
        return sb.toString();
    }

    private static String formatScore(double score) {
        return String.format("%.1f", score);
    }
}

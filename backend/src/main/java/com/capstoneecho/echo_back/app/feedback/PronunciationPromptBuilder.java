package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// LLM 에 보낼 발음 코칭 프롬프트를 만든다. directive 텍스트는 prompts.yaml 에서 오고,
// 본 클래스는 directive 뒤에 [학습 정보] / [재연습 결과] 같은 입력 블록을 붙여 완성한다.
//
// 모든 프롬프트 앞에는 system (시스템 프롬프트 / 도메인 지식) 이 자동으로 prepend 된다.
// 비어 있으면 prepend 안 함.
@Component
public class PronunciationPromptBuilder {

    private static final String KEY_STEP = "step";
    private static final String KEY_UNIT = "unit";
    private static final String KEY_RETRY = "retry";
    private static final String KEY_PRACTICE_WORD = "practice-word";

    private final PromptTemplates templates;

    public PronunciationPromptBuilder(PromptTemplates templates) {
        this.templates = templates;
    }

    public String buildStepPrompt(
            String targetText,
            double stepScore,
            List<String> perceived,
            List<String> canonical,
            List<PhonemeErrorResponse> errors
    ) {
        var sb = head(KEY_STEP).append("\n\n[학습 정보]\n");
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
        var sb = head(KEY_UNIT).append("\n\n[누적 결과]\n");
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
        var sb = head(KEY_RETRY).append("\n\n[재연습 결과]\n");
        sb.append("- 재연습 단어: ").append(safeText(practiceWord)).append('\n');
        sb.append("- 정답 음소: ").append(joinPhonemes(canonical)).append('\n');
        sb.append("- 인식 음소: ").append(joinPhonemes(perceived));
        return sb.toString();
    }

    public Map<String, Object> stepSchema() {
        return templates.schema(KEY_STEP);
    }

    public Map<String, Object> retrySchema() {
        return templates.schema(KEY_RETRY);
    }

    public String buildPracticeWordPrompt(String unitTitle, String weakPhoneme) {
        var sb = head(KEY_PRACTICE_WORD).append("\n\n[입력]\n");
        sb.append("- 학습 단원: ").append(safeText(unitTitle)).append('\n');
        sb.append("- 약점 음소: ").append(safeText(weakPhoneme));
        return sb.toString();
    }

    public Map<String, Object> practiceWordSchema() {
        return templates.schema(KEY_PRACTICE_WORD);
    }

    // 시스템 프롬프트가 있으면 directive 앞에 한 블록으로 붙인다. 비어 있으면 directive 만.
    private StringBuilder head(String key) {
        var system = templates.system();
        var directive = templates.prompt(key);
        var sb = new StringBuilder();
        if (!system.isEmpty()) {
            sb.append("[시스템]\n").append(system).append("\n\n");
        }
        sb.append(directive);
        return sb;
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

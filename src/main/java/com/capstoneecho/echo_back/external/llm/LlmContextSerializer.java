package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// LLM 프롬프트의 {{변수}} 자리에 들어갈 사람-가독성 텍스트로 컨텍스트를 직렬화한다.
// 모델이 JSON 을 다시 읽기 좋게 가벼운 들여쓰기 / 라벨링 만 한다.
public final class LlmContextSerializer {

    private LlmContextSerializer() {}

    public static String list(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(없음)";
        }
        return String.join(" ", values);
    }

    public static String errors(List<AnalyzeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "(없음)";
        }
        return errors.stream()
                .map(e -> String.format(
                        "  - op=%s canonical=%s perceived=%s canonicalIndex=%s",
                        nullable(e.op()),
                        nullable(e.canonical()),
                        nullable(e.perceived()),
                        e.canonicalIndex() == null ? "-" : e.canonicalIndex().toString()))
                .collect(Collectors.joining("\n"));
    }

    public static String priorAttempts(List<PriorAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return "(이번이 첫 시도)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (PriorAttempt a : attempts) {
            sb.append(String.format("  [%d] %s — score=%s perceived=%s\n",
                    i++,
                    a.attemptedAt() == null ? "-" : DateTimeFormatter.ISO_INSTANT.format(a.attemptedAt()),
                    a.score() == null ? "-" : String.format("%.1f", a.score()),
                    nullable(a.perceived())));
            if (!a.errors().isEmpty()) {
                sb.append("      errors:\n");
                for (AnalyzeError err : a.errors()) {
                    sb.append(String.format("        - %s %s→%s (idx=%s)%n",
                            nullable(err.op()),
                            nullable(err.canonical()),
                            nullable(err.perceived()),
                            err.canonicalIndex() == null ? "-" : err.canonicalIndex().toString()));
                }
            }
        }
        return sb.toString().stripTrailing();
    }

    public static String stepSummaries(List<LlmComprehensiveContext.StepSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "(없음)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (LlmComprehensiveContext.StepSummary s : summaries) {
            sb.append(String.format(
                    "  Step %d: \"%s\" — best=%.1f attempts=%d weak=%s%n",
                    i++,
                    s.targetText(),
                    s.bestScore(),
                    s.attempts(),
                    nullable(s.weakPhoneme())));
        }
        return sb.toString().stripTrailing();
    }

    public static String number(Double value) {
        return value == null ? "-" : String.format("%.1f", value);
    }

    private static String nullable(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}

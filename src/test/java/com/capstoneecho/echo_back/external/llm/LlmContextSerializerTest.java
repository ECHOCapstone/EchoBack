package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmContextSerializerTest {

    @Test
    @DisplayName("list: 빈/null 입력은 (없음) 으로 정규화")
    void listNormalizesEmpty() {
        assertThat(LlmContextSerializer.list(null)).isEqualTo("(없음)");
        assertThat(LlmContextSerializer.list(List.of())).isEqualTo("(없음)");
        assertThat(LlmContextSerializer.list(List.of("HH", "AH"))).isEqualTo("HH AH");
    }

    @Test
    @DisplayName("errors: 항목별로 op / canonical / perceived / canonicalIndex 라벨링")
    void errorsAreLabeled() {
        AnalyzeError err = new AnalyzeError("SUB", 2, "AH", "EH");
        String s = LlmContextSerializer.errors(List.of(err));
        assertThat(s).contains("op=SUB").contains("canonical=EH").contains("perceived=AH")
                .contains("canonicalIndex=2");
    }

    @Test
    @DisplayName("errors: null 입력은 (없음) 으로 정규화")
    void errorsNullReturnsEmptyMarker() {
        assertThat(LlmContextSerializer.errors(null)).isEqualTo("(없음)");
        assertThat(LlmContextSerializer.errors(List.of())).isEqualTo("(없음)");
    }

    @Test
    @DisplayName("priorAttempts: 빈 리스트는 (이번이 첫 시도) 로 표시")
    void priorAttemptsEmpty() {
        assertThat(LlmContextSerializer.priorAttempts(null)).isEqualTo("(이번이 첫 시도)");
        assertThat(LlmContextSerializer.priorAttempts(List.of())).isEqualTo("(이번이 첫 시도)");
    }

    @Test
    @DisplayName("priorAttempts: 시도마다 인덱스 / 시간 / 점수 / errors 라벨링")
    void priorAttemptsAreNumbered() {
        PriorAttempt a = new PriorAttempt(
                Instant.parse("2026-01-01T00:00:00Z"),
                85.5,
                "HH AH",
                List.of(new AnalyzeError("SUB", 1, "AH", "EH")));
        String s = LlmContextSerializer.priorAttempts(List.of(a));
        assertThat(s).contains("[1]").contains("score=85.5").contains("perceived=HH AH")
                .contains("errors:").contains("SUB");
    }

    @Test
    @DisplayName("stepSummaries: 비어 있으면 (없음)")
    void stepSummariesEmpty() {
        assertThat(LlmContextSerializer.stepSummaries(null)).isEqualTo("(없음)");
        assertThat(LlmContextSerializer.stepSummaries(List.of())).isEqualTo("(없음)");
    }

    @Test
    @DisplayName("stepSummaries: 항목별로 best / attempts / weak 라벨링")
    void stepSummariesLabeled() {
        LlmComprehensiveContext.StepSummary s1 = new LlmComprehensiveContext.StepSummary(
                "hello", 2, 88.0, "AH");
        String s = LlmContextSerializer.stepSummaries(List.of(s1));
        assertThat(s).contains("Step 1").contains("hello").contains("attempts=2")
                .contains("weak=AH");
    }

    @Test
    @DisplayName("number: null 은 - / 값은 소수 한 자리")
    void number() {
        assertThat(LlmContextSerializer.number(null)).isEqualTo("-");
        assertThat(LlmContextSerializer.number(80.0)).isEqualTo("80.0");
    }
}

package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// LLM 구조화 출력에 쓰이는 record 들의 정규화 / 검증 / clamp 동작 확인.
class StructuredOutputRecordsTest {

    @Test
    @DisplayName("LlmStepFeedback: score 는 0~100 으로 clamp, 리스트는 null-safe")
    void stepFeedbackNormalizes() {
        LlmStepFeedback over = new LlmStepFeedback(
                150, true, "g", null, null, null, null);
        LlmStepFeedback under = new LlmStepFeedback(
                -10, false, "g", null, null, null, null);
        assertThat(over.score()).isEqualTo(100);
        assertThat(under.score()).isEqualTo(0);
        assertThat(over.strengths()).isEmpty();
        assertThat(over.wrongWords()).isEmpty();
        assertThat(over.phonemeTips()).isEmpty();
    }

    @Test
    @DisplayName("LlmRetryFeedback: score clamp + 빈 phonemeTips 폴백")
    void retryFeedbackNormalizes() {
        LlmRetryFeedback f = new LlmRetryFeedback(200, true, false, "ok", null);
        assertThat(f.score()).isEqualTo(100);
        assertThat(f.phonemeTips()).isEmpty();
    }

    @Test
    @DisplayName("LlmComprehensiveFeedback: 모든 리스트는 null-safe")
    void comprehensiveNullSafe() {
        LlmComprehensiveFeedback f = new LlmComprehensiveFeedback(
                -1, null, null, null, null);
        assertThat(f.overallScore()).isEqualTo(0);
        assertThat(f.summaryKr()).isEmpty();
        assertThat(f.strengths()).isEmpty();
        assertThat(f.weaknesses()).isEmpty();
        assertThat(f.nextPracticeItems()).isEmpty();
    }

    @Test
    @DisplayName("PracticeItem: text / kind 필수, reason null 입력은 빈 문자열")
    void practiceItemValidatesAndNormalizes() {
        PracticeItem ok = new PracticeItem("the", PracticeItem.Kind.WORD, null);
        assertThat(ok.reason()).isEmpty();

        assertThatThrownBy(() -> new PracticeItem(" ", PracticeItem.Kind.WORD, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PracticeItem("ok", null, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PhonemeTip: 모든 필드 null-safe")
    void phonemeTipNullSafe() {
        PhonemeTip t = new PhonemeTip(null, null, null);
        assertThat(t.phoneme()).isEmpty();
        assertThat(t.koreanCue()).isEmpty();
        assertThat(t.tip()).isEmpty();
    }

    @Test
    @DisplayName("PriorAttempt: errors null-safe")
    void priorAttemptNullSafe() {
        PriorAttempt a = new PriorAttempt(Instant.now(), 80.0, "p", null);
        assertThat(a.errors()).isEmpty();

        AnalyzeError err = new AnalyzeError("SUB", 1, "AH", "EH");
        PriorAttempt b = new PriorAttempt(Instant.now(), null, null, List.of(err));
        assertThat(b.errors()).containsExactly(err);
    }

    @Test
    @DisplayName("Context records: 입력 null 은 모두 빈 값으로 정규화")
    void contextRecordsAreDefensive() {
        LlmStepContext step = new LlmStepContext(
                null, null, null, null, null, null, null, null, null);
        assertThat(step.chapterTitle()).isEmpty();
        assertThat(step.targetText()).isEmpty();
        assertThat(step.perceived()).isEmpty();
        assertThat(step.priorAttempts()).isEmpty();

        LlmRetryContext retry = new LlmRetryContext(
                null, null, null, null, null, null, null);
        assertThat(retry.word()).isEmpty();
        assertThat(retry.errors()).isEmpty();

        LlmComprehensiveContext comp = new LlmComprehensiveContext(
                null, null, null, null, null, 0.0);
        assertThat(comp.chapterTitle()).isEmpty();
        assertThat(comp.stepSummaries()).isEmpty();
        assertThat(comp.aggregatedErrors()).isEmpty();
    }
}

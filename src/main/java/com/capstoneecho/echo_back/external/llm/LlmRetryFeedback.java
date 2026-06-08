package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 단어/구 재시도 채점 결과. LLM 은 alignment / correct / 피드백만 책임지고,
// 점수는 백엔드 ScoringService 가 alignment + errors 로부터 결정적으로 계산한다.
public record LlmRetryFeedback(
        List<AlignmentOp> alignment,
        List<LlmPhonemeError> errors,
        boolean correct,
        boolean retryRecommended,
        String guidanceKr,
        PronunciationGuide pronunciationGuide,
        List<PhonemeTip> phonemeTips
) {

    @JsonCreator
    public LlmRetryFeedback(
            @JsonProperty("alignment") List<AlignmentOp> alignment,
            @JsonProperty("errors") List<LlmPhonemeError> errors,
            @JsonProperty("correct") boolean correct,
            @JsonProperty("retryRecommended") boolean retryRecommended,
            @JsonProperty("guidanceKr") String guidanceKr,
            @JsonProperty("pronunciationGuide") PronunciationGuide pronunciationGuide,
            @JsonProperty("phonemeTips") List<PhonemeTip> phonemeTips) {
        this.alignment = alignment == null ? List.of() : List.copyOf(alignment);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.correct = correct;
        this.retryRecommended = retryRecommended;
        this.guidanceKr = guidanceKr == null ? "" : guidanceKr;
        this.pronunciationGuide = pronunciationGuide == null ? PronunciationGuide.empty() : pronunciationGuide;
        this.phonemeTips = phonemeTips == null ? List.of() : List.copyOf(phonemeTips);
    }
}

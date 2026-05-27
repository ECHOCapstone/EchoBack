package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 단어/구 재시도 단계의 LLM 구조화 결과.
// correct 는 LLM 의 정성 판정 (점수 임계만으로는 부족한 케이스 보정). guidanceKr 은 항상 non-empty.
public record LlmRetryFeedback(
        int score,
        boolean correct,
        boolean retryRecommended,
        String guidanceKr,
        PronunciationGuide pronunciationGuide,
        List<PhonemeTip> phonemeTips
) {

    @JsonCreator
    public LlmRetryFeedback(
            @JsonProperty("score") int score,
            @JsonProperty("correct") boolean correct,
            @JsonProperty("retryRecommended") boolean retryRecommended,
            @JsonProperty("guidanceKr") String guidanceKr,
            @JsonProperty("pronunciationGuide") PronunciationGuide pronunciationGuide,
            @JsonProperty("phonemeTips") List<PhonemeTip> phonemeTips) {
        this.score = clamp(score);
        this.correct = correct;
        this.retryRecommended = retryRecommended;
        this.guidanceKr = guidanceKr == null ? "" : guidanceKr;
        this.pronunciationGuide = pronunciationGuide == null ? PronunciationGuide.empty() : pronunciationGuide;
        this.phonemeTips = phonemeTips == null ? List.of() : List.copyOf(phonemeTips);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }
}

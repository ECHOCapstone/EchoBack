package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 단어/구 재시도 단계의 LLM 구조화 결과.
//   score / alignment / errors : step 과 동일한 의미.
//   correct                   : LLM 정성 판정 (점수 임계만으로 부족한 케이스 보정).
//   retryRecommended           : 추가 재시도 권고.
//   guidanceKr / pronunciationGuide / phonemeTips : 학습자 노출.
public record LlmRetryFeedback(
        int score,
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
            @JsonProperty("score") int score,
            @JsonProperty("alignment") List<AlignmentOp> alignment,
            @JsonProperty("errors") List<LlmPhonemeError> errors,
            @JsonProperty("correct") boolean correct,
            @JsonProperty("retryRecommended") boolean retryRecommended,
            @JsonProperty("guidanceKr") String guidanceKr,
            @JsonProperty("pronunciationGuide") PronunciationGuide pronunciationGuide,
            @JsonProperty("phonemeTips") List<PhonemeTip> phonemeTips) {
        this.score = LlmScores.clampScore(score);
        this.alignment = alignment == null ? List.of() : List.copyOf(alignment);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.correct = correct;
        this.retryRecommended = retryRecommended;
        this.guidanceKr = guidanceKr == null ? "" : guidanceKr;
        this.pronunciationGuide = pronunciationGuide == null ? PronunciationGuide.empty() : pronunciationGuide;
        this.phonemeTips = phonemeTips == null ? List.of() : List.copyOf(phonemeTips);
    }
}

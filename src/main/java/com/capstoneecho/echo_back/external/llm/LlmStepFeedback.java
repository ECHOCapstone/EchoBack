package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 한 번의 녹음 (step) 에 대해 LLM 이 돌려주는 구조화 결과.
//   score              : 0~100 정수 (clamp 보장). 합격선 비교의 단일 출처.
//   alignment          : canonical ↔ perceived 정렬 시퀀스 (MATCH 포함). FE 시각화 source.
//   errors             : alignment 에서 추출한 비-MATCH 항목 (약점 추출 source).
//   retryRecommended   : LLM 정성 판단 (점수만으로 못 잡는 케이스 보정).
//   guidanceKr / pronunciationGuide / strengths / weaknesses / wrongWords / phonemeTips : 학습자 노출.
public record LlmStepFeedback(
        int score,
        List<AlignmentOp> alignment,
        List<LlmPhonemeError> errors,
        boolean retryRecommended,
        String guidanceKr,
        PronunciationGuide pronunciationGuide,
        List<String> strengths,
        List<String> weaknesses,
        List<WrongWord> wrongWords,
        List<PhonemeTip> phonemeTips
) {

    @JsonCreator
    public LlmStepFeedback(
            @JsonProperty("score") int score,
            @JsonProperty("alignment") List<AlignmentOp> alignment,
            @JsonProperty("errors") List<LlmPhonemeError> errors,
            @JsonProperty("retryRecommended") boolean retryRecommended,
            @JsonProperty("guidanceKr") String guidanceKr,
            @JsonProperty("pronunciationGuide") PronunciationGuide pronunciationGuide,
            @JsonProperty("strengths") List<String> strengths,
            @JsonProperty("weaknesses") List<String> weaknesses,
            @JsonProperty("wrongWords") List<WrongWord> wrongWords,
            @JsonProperty("phonemeTips") List<PhonemeTip> phonemeTips) {
        this.score = LlmScores.clampScore(score);
        this.alignment = alignment == null ? List.of() : List.copyOf(alignment);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.retryRecommended = retryRecommended;
        this.guidanceKr = guidanceKr == null ? "" : guidanceKr;
        this.pronunciationGuide = pronunciationGuide == null ? PronunciationGuide.empty() : pronunciationGuide;
        this.strengths = strengths == null ? List.of() : List.copyOf(strengths);
        this.weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
        this.wrongWords = wrongWords == null ? List.of() : List.copyOf(wrongWords);
        this.phonemeTips = phonemeTips == null ? List.of() : List.copyOf(phonemeTips);
    }
}

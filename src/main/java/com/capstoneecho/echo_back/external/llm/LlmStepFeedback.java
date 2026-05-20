package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 한 번의 녹음 (step) 에 대해 LLM 이 돌려주는 구조화 결과.
// score 는 0~100 정수 (clamp 보장). retryRecommended 는 점수만이 아닌 LLM 판단도 반영한다.
// wrongWords 는 targetText 의 단어 인덱스 기준. phonemeTips 는 약점 음소별 한국식 단서 (아학편).
public record LlmStepFeedback(
        int score,
        boolean retryRecommended,
        String guidanceKr,
        List<String> strengths,
        List<String> weaknesses,
        List<WrongWord> wrongWords,
        List<PhonemeTip> phonemeTips
) {

    @JsonCreator
    public LlmStepFeedback(
            @JsonProperty("score") int score,
            @JsonProperty("retryRecommended") boolean retryRecommended,
            @JsonProperty("guidanceKr") String guidanceKr,
            @JsonProperty("strengths") List<String> strengths,
            @JsonProperty("weaknesses") List<String> weaknesses,
            @JsonProperty("wrongWords") List<WrongWord> wrongWords,
            @JsonProperty("phonemeTips") List<PhonemeTip> phonemeTips) {
        this.score = clamp(score);
        this.retryRecommended = retryRecommended;
        this.guidanceKr = guidanceKr == null ? "" : guidanceKr;
        this.strengths = strengths == null ? List.of() : List.copyOf(strengths);
        this.weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
        this.wrongWords = wrongWords == null ? List.of() : List.copyOf(wrongWords);
        this.phonemeTips = phonemeTips == null ? List.of() : List.copyOf(phonemeTips);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }
}

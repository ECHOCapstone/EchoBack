package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 한 챕터 (또는 세션) 의 모든 step 이 끝난 뒤 LLM 이 돌려주는 종합 결과.
// nextPracticeItems 는 약점 음소 / 자주 틀린 단어 / 챕터 주제를 종합해 단어 · 구 · 문장 혼합으로 추천한다.
public record LlmComprehensiveFeedback(
        int overallScore,
        String summaryKr,
        List<String> strengths,
        List<String> weaknesses,
        List<PracticeItem> nextPracticeItems
) {

    @JsonCreator
    public LlmComprehensiveFeedback(
            @JsonProperty("overallScore") int overallScore,
            @JsonProperty("summaryKr") String summaryKr,
            @JsonProperty("strengths") List<String> strengths,
            @JsonProperty("weaknesses") List<String> weaknesses,
            @JsonProperty("nextPracticeItems") List<PracticeItem> nextPracticeItems) {
        this.overallScore = clamp(overallScore);
        this.summaryKr = summaryKr == null ? "" : summaryKr;
        this.strengths = strengths == null ? List.of() : List.copyOf(strengths);
        this.weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
        this.nextPracticeItems = nextPracticeItems == null ? List.of() : List.copyOf(nextPracticeItems);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }
}

package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.util.List;

// 한 챕터 종합 피드백 시 LLM 에 전달하는 입력.
// stepSummaries 는 각 step 의 최종 결과 (점수 + 약점 + 시도 횟수) — 챕터 전반의 흐름을 요약하도록 도움.
// aggregatedErrors 는 챕터의 모든 시도에서 모인 음소 오류 — 가장 빈번한 약점 추출의 원천.
public record LlmComprehensiveContext(
        String chapterTitle,
        String chapterContent,
        List<StepSummary> stepSummaries,
        List<AnalyzeError> aggregatedErrors,
        String dominantWeakPhoneme,
        double overallAccuracy
) {
    public LlmComprehensiveContext {
        chapterTitle = chapterTitle == null ? "" : chapterTitle;
        chapterContent = chapterContent == null ? "" : chapterContent;
        stepSummaries = stepSummaries == null ? List.of() : List.copyOf(stepSummaries);
        aggregatedErrors = aggregatedErrors == null ? List.of() : List.copyOf(aggregatedErrors);
    }

    // 한 step 의 최종 결과 요약. attempts 는 통과까지 든 시도 횟수.
    public record StepSummary(
            String targetText,
            int attempts,
            double bestScore,
            String weakPhoneme
    ) {
        public StepSummary {
            targetText = targetText == null ? "" : targetText;
        }
    }
}

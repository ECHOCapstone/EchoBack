package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pWord;
import java.util.List;

// 한 step 분석 시 LLM 에 전달하는 입력. 모든 리스트는 null 입력 시 빈 리스트로 정규화.
// priorAttempts 는 같은 (사용자, step) 의 이전 시도들 — 누적 피드백을 위해 LLM 이 참조한다.
public record LlmStepContext(
        String chapterTitle,
        String targetText,
        List<String> perceived,
        List<String> canonical,
        List<AnalyzeError> errors,
        List<G2pWord> g2pWords,
        String weakPhoneme,
        Double currentScore,
        List<PriorAttempt> priorAttempts
) {
    public LlmStepContext {
        chapterTitle = chapterTitle == null ? "" : chapterTitle;
        targetText = targetText == null ? "" : targetText;
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        canonical = canonical == null ? List.of() : List.copyOf(canonical);
        errors = errors == null ? List.of() : List.copyOf(errors);
        g2pWords = g2pWords == null ? List.of() : List.copyOf(g2pWords);
        priorAttempts = priorAttempts == null ? List.of() : List.copyOf(priorAttempts);
    }
}

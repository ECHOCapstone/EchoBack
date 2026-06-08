package com.capstoneecho.echo_back.external.llm;

import java.util.Map;
import org.springframework.stereotype.Component;

// Gemini REST API 의 generateContent + 구조화 출력으로 step / retry / comprehensive 채점 응답을 받는다.
// canonical 자체는 별도 호출 (GeminiCanonicalGenerator) 이 만들고, 본 클라이언트는 그 결과를 입력으로
// 받아 채점 / 피드백만 수행한다. 시스템 프롬프트는 PromptCatalog.system 을 매 요청에 주입한다.
//
// step / retry 응답이 null 이면 (응답 비어있음 / 파싱 실패) 폴백으로 떨어진다 — provider 선택은
// DispatchingLlmClient 가 한다.
@Component
public class GeminiLlmClient implements LlmClient {

    private final GeminiCallExecutor executor;
    private final PromptCatalog prompts;
    private final RuleBasedLlmFallback fallback;

    public GeminiLlmClient(
            GeminiCallExecutor executor,
            PromptCatalog prompts,
            RuleBasedLlmFallback fallback) {
        this.executor = executor;
        this.prompts = prompts;
        this.fallback = fallback;
    }

    public boolean isAvailable() {
        return executor.isAvailable();
    }

    @Override
    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        String userPrompt = prompts.render("step-feedback", Map.of(
                "chapterTitle", context.chapterTitle(),
                "targetText", context.targetText(),
                "canonicalWords", LlmContextSerializer.canonicalWords(context.canonicalWords()),
                "perceived", LlmContextSerializer.list(context.perceived()),
                "priorAttempts", LlmContextSerializer.priorAttempts(context.priorAttempts()),
                "alignment", LlmContextSerializer.alignment(context.referenceAlignment())));
        LlmStepFeedback parsed = executor.callOrNull(
                prompts.raw("system"), userPrompt, LlmJsonSchemas.stepFeedback(), LlmStepFeedback.class);
        return parsed != null ? parsed : fallback.stepFeedback(context);
    }

    @Override
    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        String userPrompt = prompts.render("retry-feedback", Map.of(
                "word", context.word(),
                "canonicalWords", LlmContextSerializer.canonicalWords(context.canonicalWords()),
                "perceived", LlmContextSerializer.list(context.perceived()),
                "priorAttempts", LlmContextSerializer.priorAttempts(context.priorAttempts()),
                "alignment", LlmContextSerializer.alignment(context.referenceAlignment())));
        LlmRetryFeedback parsed = executor.callOrNull(
                prompts.raw("system"), userPrompt, LlmJsonSchemas.retryFeedback(), LlmRetryFeedback.class);
        return parsed != null ? parsed : fallback.retryFeedback(context);
    }

    @Override
    public LlmComprehensiveFeedback comprehensiveFeedback(LlmComprehensiveContext context) {
        String userPrompt = prompts.render("comprehensive-feedback", Map.of(
                "chapterTitle", context.chapterTitle(),
                "chapterContent", context.chapterContent(),
                "overallAccuracy", LlmContextSerializer.number(context.overallAccuracy()),
                "dominantWeakPhoneme", nullSafe(context.dominantWeakPhoneme()),
                "stepSummaries", LlmContextSerializer.stepSummaries(context.stepSummaries()),
                "aggregatedErrors", LlmContextSerializer.errors(context.aggregatedErrors())));
        LlmComprehensiveFeedback parsed = executor.callOrNull(
                prompts.raw("system"), userPrompt,
                LlmJsonSchemas.comprehensiveFeedback(), LlmComprehensiveFeedback.class);
        return parsed != null ? parsed : fallback.comprehensiveFeedback(context);
    }

    private static String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "(미확정)" : s;
    }
}

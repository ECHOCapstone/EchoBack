package com.capstoneecho.echo_back.external.llm;

import java.util.Map;
import org.springframework.stereotype.Component;

// Gemini REST API 의 generateContent + 구조화 출력 (responseMimeType + responseSchema) 을 사용해
// 세 종류의 피드백을 JSON 으로 받아 record 로 역직렬화한다.
// 시스템 프롬프트 (아학편 가이드 포함) 는 PromptCatalog 의 system.md 에서 로드해
// systemInstruction 필드로 매 요청에 주입한다.
// 항상 등록되며, 실제 호출 여부는 DispatchingLlmClient 가 활성 provider 와 isAvailable() 로 결정한다.
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

    // apiKey 가 설정돼 호출 가능한 상태인지. DispatchingLlmClient 가 라우팅 판단에 쓴다.
    public boolean isAvailable() {
        return executor.isAvailable();
    }

    @Override
    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        String userPrompt = prompts.render("step-feedback", Map.of(
                "chapterTitle", context.chapterTitle(),
                "targetText", context.targetText(),
                "perceived", LlmContextSerializer.list(context.perceived()),
                "canonical", LlmContextSerializer.list(context.canonical()),
                "canonicalWords", LlmContextSerializer.canonicalWords(context.canonicalWords()),
                "priorAttempts", LlmContextSerializer.priorAttempts(context.priorAttempts())));
        LlmStepFeedback parsed = executor.callOrNull(
                prompts.raw("system"), userPrompt, LlmJsonSchemas.stepFeedback(), LlmStepFeedback.class);
        return parsed != null ? parsed : fallback.stepFeedback(context);
    }

    @Override
    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        String userPrompt = prompts.render("retry-feedback", Map.of(
                "word", context.word(),
                "perceived", LlmContextSerializer.list(context.perceived()),
                "canonical", LlmContextSerializer.list(context.canonical()),
                "canonicalWords", LlmContextSerializer.canonicalWords(context.canonicalWords()),
                "priorAttempts", LlmContextSerializer.priorAttempts(context.priorAttempts())));
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

package com.capstoneecho.echo_back.external.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// app.llm.provider=rule-based (기본) 일 때 컨텍스트에 등록되는 LlmClient 구현체.
// 모든 분석 결과를 RuleBasedLlmFallback 으로 위임해 외부 호출 없이 결정적으로 채운다.
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedLlmFeedbackGenerator implements LlmClient {

    private final RuleBasedLlmFallback fallback;

    public RuleBasedLlmFeedbackGenerator(RuleBasedLlmFallback fallback) {
        this.fallback = fallback;
    }

    @Override
    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        return fallback.stepFeedback(context);
    }

    @Override
    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        return fallback.retryFeedback(context);
    }

    @Override
    public LlmComprehensiveFeedback comprehensiveFeedback(LlmComprehensiveContext context) {
        return fallback.comprehensiveFeedback(context);
    }
}

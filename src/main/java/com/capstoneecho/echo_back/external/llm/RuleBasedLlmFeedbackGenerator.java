package com.capstoneecho.echo_back.external.llm;

import org.springframework.stereotype.Component;

// 외부 호출 없이 결정적으로 피드백을 채우는 규칙 기반 LlmClient 구현체.
// 모든 분석 결과를 RuleBasedLlmFallback 으로 위임한다. 항상 등록되며, 활성 provider 선택은
// DispatchingLlmClient 가 런타임에 수행한다.
@Component
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

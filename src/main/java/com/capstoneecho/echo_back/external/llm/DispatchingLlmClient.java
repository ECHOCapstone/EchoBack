package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// 활성 provider 를 런타임에 골라 호출을 위임하는 LlmClient. 서비스는 이 빈(@Primary)을 주입받는다.
// provider 기본값은 app.llm.provider(yaml) 이며 어드민이 SettingsService 로 덮어쓸 수 있다.
// gemini 가 선택돼도 apiKey 가 없으면(isAvailable=false) 규칙 기반으로 안전하게 떨어진다.
@Component
@Primary
public class DispatchingLlmClient implements LlmClient {

    static final String PROVIDER_GEMINI = "gemini";
    static final String PROVIDER_RULE_BASED = "rule-based";

    private final RuleBasedLlmFeedbackGenerator ruleBased;
    private final GeminiLlmClient gemini;
    private final SettingsService settings;
    private final String defaultProvider;

    public DispatchingLlmClient(
            RuleBasedLlmFeedbackGenerator ruleBased,
            GeminiLlmClient gemini,
            SettingsService settings,
            AppProperties appProperties) {
        this.ruleBased = ruleBased;
        this.gemini = gemini;
        this.settings = settings;
        AppProperties.Llm llm = appProperties.llm();
        String configured = llm == null ? null : llm.provider();
        this.defaultProvider =
                (configured == null || configured.isBlank()) ? PROVIDER_RULE_BASED : configured;
    }

    private LlmClient active() {
        String provider = settings.getOrDefault(LlmSettingKeys.PROVIDER, defaultProvider);
        if (PROVIDER_GEMINI.equals(provider) && gemini.isAvailable()) {
            return gemini;
        }
        return ruleBased;
    }

    @Override
    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        return active().stepFeedback(context);
    }

    @Override
    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        return active().retryFeedback(context);
    }

    @Override
    public LlmComprehensiveFeedback comprehensiveFeedback(LlmComprehensiveContext context) {
        return active().comprehensiveFeedback(context);
    }
}

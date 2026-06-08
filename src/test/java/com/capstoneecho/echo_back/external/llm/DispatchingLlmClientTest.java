package com.capstoneecho.echo_back.external.llm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DispatchingLlmClientTest {

    private final SettingsService settings = mock(SettingsService.class);
    private final RuleBasedLlmFeedbackGenerator ruleBased = mock(RuleBasedLlmFeedbackGenerator.class);
    private final GeminiLlmClient gemini = mock(GeminiLlmClient.class);

    private DispatchingLlmClient dispatcher(String defaultProvider) {
        AppProperties props = new AppProperties(
                null, null, null, new AppProperties.Llm(defaultProvider, null),
                null, null, null, null,
                null,
                null, null, null, null, null, null, null, null);
        return new DispatchingLlmClient(ruleBased, gemini, settings, props);
    }

    private static LlmStepContext context() {
        return new LlmStepContext(null, null, null, null, null, null);
    }

    @Test
    @DisplayName("활성 provider 가 rule-based 면 규칙 기반으로 위임한다")
    void routesToRuleBased() {
        when(settings.getOrDefault(LlmSettingKeys.PROVIDER, "rule-based")).thenReturn("rule-based");
        LlmStepContext ctx = context();

        dispatcher("rule-based").stepFeedback(ctx);

        verify(ruleBased).stepFeedback(ctx);
        verify(gemini, never()).stepFeedback(ctx);
    }

    @Test
    @DisplayName("gemini 선택 + 사용 가능하면 gemini 로 위임한다")
    void routesToGeminiWhenAvailable() {
        when(settings.getOrDefault(LlmSettingKeys.PROVIDER, "rule-based")).thenReturn("gemini");
        when(gemini.isAvailable()).thenReturn(true);
        LlmStepContext ctx = context();

        dispatcher("rule-based").stepFeedback(ctx);

        verify(gemini).stepFeedback(ctx);
        verify(ruleBased, never()).stepFeedback(ctx);
    }

    @Test
    @DisplayName("gemini 선택이어도 사용 불가하면 규칙 기반으로 떨어진다")
    void fallsBackWhenGeminiUnavailable() {
        when(settings.getOrDefault(LlmSettingKeys.PROVIDER, "rule-based")).thenReturn("gemini");
        when(gemini.isAvailable()).thenReturn(false);
        LlmStepContext ctx = context();

        dispatcher("rule-based").stepFeedback(ctx);

        verify(ruleBased).stepFeedback(ctx);
        verify(gemini, never()).stepFeedback(ctx);
    }
}

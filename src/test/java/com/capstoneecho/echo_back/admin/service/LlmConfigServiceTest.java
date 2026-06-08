package com.capstoneecho.echo_back.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.admin.dto.LlmConfigResponse;
import com.capstoneecho.echo_back.external.llm.GeminiLlmClient;
import com.capstoneecho.echo_back.external.llm.LlmSettingKeys;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmConfigServiceTest {

    private final SettingsService settings = mock(SettingsService.class);
    private final GeminiLlmClient gemini = mock(GeminiLlmClient.class);

    private LlmConfigService service() {
        AppProperties props = new AppProperties(
                null, null, null,
                new AppProperties.Llm("rule-based", new AppProperties.Llm.Gemini(
                        "", "gemini-3.1-flash-lite", "url", 10_000L,
                        List.of("gemini-3.1-flash-lite", "gemini-2.5-flash"))),
                null, null, null, null,
                null,
                null, null, null, null, null, null, null, null);
        return new LlmConfigService(settings, gemini, props);
    }

    @Test
    @DisplayName("current 는 적용값 + 후보 목록을 노출한다")
    void currentExposesEffectiveValuesAndOptions() {
        when(settings.getOrDefault(LlmSettingKeys.PROVIDER, "rule-based")).thenReturn("rule-based");
        when(settings.getOrDefault(LlmSettingKeys.GEMINI_MODEL, "gemini-3.1-flash-lite"))
                .thenReturn("gemini-3.1-flash-lite");
        when(gemini.isAvailable()).thenReturn(false);

        LlmConfigResponse r = service().current();

        assertThat(r.provider()).isEqualTo("rule-based");
        assertThat(r.providerOptions()).containsExactly("rule-based", "gemini");
        assertThat(r.modelOptions()).contains("gemini-2.5-flash");
        assertThat(r.geminiAvailable()).isFalse();
    }

    @Test
    @DisplayName("rule-based 로 바꾸면 provider 만 저장한다")
    void updateRuleBasedSetsProviderOnly() {
        service().update("rule-based", null);

        verify(settings).set(LlmSettingKeys.PROVIDER, "rule-based");
        verify(settings, never()).set(eq(LlmSettingKeys.GEMINI_MODEL), any());
    }

    @Test
    @DisplayName("gemini + 사용 가능 + 허용 모델이면 provider/model 모두 저장한다")
    void updateGeminiValidSetsBoth() {
        when(gemini.isAvailable()).thenReturn(true);

        service().update("gemini", "gemini-2.5-flash");

        verify(settings).set(LlmSettingKeys.GEMINI_MODEL, "gemini-2.5-flash");
        verify(settings).set(LlmSettingKeys.PROVIDER, "gemini");
    }

    @Test
    @DisplayName("지원하지 않는 provider 는 거부한다")
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> service().update("openai", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("apiKey 없는 gemini 선택은 거부한다")
    void rejectsGeminiWhenUnavailable() {
        when(gemini.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service().update("gemini", "gemini-2.5-flash"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("허용 목록에 없는 모델은 거부한다")
    void rejectsUnknownModel() {
        when(gemini.isAvailable()).thenReturn(true);

        assertThatThrownBy(() -> service().update("gemini", "gpt-4o"))
                .isInstanceOf(BusinessException.class);
    }
}

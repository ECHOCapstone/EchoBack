package com.capstoneecho.echo_back.external.llm.translation;

import com.capstoneecho.echo_back.external.llm.LlmSettingKeys;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// 활성 LLM provider 를 런타임에 골라 번역 호출을 위임한다. provider 신호는 LLM 피드백과 같은 키를 재사용해
// 어드민이 "설정" 탭에서 한 곳에서 토글하면 피드백과 번역이 함께 전환된다.
//   gemini   : GeminiTranslationClient (apiKey 가 있어야 함)
//   그 외 / Gemini 비활성 : NoopTranslationClient — 빈 결과로 폴백.
@Component
@Primary
public class DispatchingTranslationClient implements TranslationClient {

    private static final String PROVIDER_GEMINI = "gemini";

    private final GeminiTranslationClient gemini;
    private final NoopTranslationClient noop;
    private final SettingsService settings;
    private final String defaultProvider;

    public DispatchingTranslationClient(
            GeminiTranslationClient gemini,
            NoopTranslationClient noop,
            SettingsService settings,
            AppProperties appProperties) {
        this.gemini = gemini;
        this.noop = noop;
        this.settings = settings;
        AppProperties.Llm llm = appProperties.llm();
        String configured = llm == null ? null : llm.provider();
        this.defaultProvider = (configured == null || configured.isBlank()) ? "rule-based" : configured;
    }

    private TranslationClient active() {
        String provider = settings.getOrDefault(LlmSettingKeys.PROVIDER, defaultProvider);
        if (PROVIDER_GEMINI.equals(provider) && gemini.isAvailable()) {
            return gemini;
        }
        return noop;
    }

    @Override
    public List<String> translate(List<String> sourceTexts) {
        return active().translate(sourceTexts);
    }
}

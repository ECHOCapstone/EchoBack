package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.admin.dto.LlmConfigResponse;
import com.capstoneecho.echo_back.external.llm.GeminiLlmClient;
import com.capstoneecho.echo_back.external.llm.LlmSettingKeys;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 어드민의 LLM provider / model 선택을 읽고 적용한다.
// yaml 기본값 위에 SettingsService 오버라이드를 얹어 현재 적용값을 노출하고, 변경 시 검증한다.
@Service
public class LlmConfigService {

    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_RULE_BASED = "rule-based";
    private static final List<String> PROVIDER_OPTIONS = List.of(PROVIDER_RULE_BASED, PROVIDER_GEMINI);

    private final SettingsService settings;
    private final GeminiLlmClient gemini;
    private final String defaultProvider;
    private final String defaultModel;
    private final List<String> modelOptions;

    public LlmConfigService(
            SettingsService settings, GeminiLlmClient gemini, AppProperties appProperties) {
        this.settings = settings;
        this.gemini = gemini;
        AppProperties.Llm llm = appProperties.llm();
        AppProperties.Llm.Gemini g = llm == null ? null : llm.gemini();
        this.defaultProvider = (llm == null || llm.provider() == null || llm.provider().isBlank())
                ? PROVIDER_RULE_BASED : llm.provider();
        this.modelOptions = g == null ? List.of() : g.safeModels();
        // 모델 기본값: yaml 의 model 이 비면 허용 목록(models)의 첫 항목으로 폴백한다 — 코드에 모델 literal 을 두지 않는다.
        this.defaultModel = (g == null || g.model() == null || g.model().isBlank())
                ? modelOptions.stream().findFirst().orElse("")
                : g.model();
    }

    public LlmConfigResponse current() {
        return new LlmConfigResponse(
                settings.getOrDefault(LlmSettingKeys.PROVIDER, defaultProvider),
                settings.getOrDefault(LlmSettingKeys.GEMINI_MODEL, defaultModel),
                gemini.isAvailable(),
                PROVIDER_OPTIONS,
                modelOptions);
    }

    @Transactional
    public LlmConfigResponse update(String provider, String model) {
        if (!PROVIDER_OPTIONS.contains(provider)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 provider: " + provider);
        }
        if (PROVIDER_GEMINI.equals(provider)) {
            if (!gemini.isAvailable()) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "gemini 는 apiKey 가 설정돼야 선택할 수 있습니다.");
            }
            if (model == null || model.isBlank() || !modelOptions.contains(model)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "허용되지 않은 모델: " + model);
            }
            settings.set(LlmSettingKeys.GEMINI_MODEL, model);
        }
        settings.set(LlmSettingKeys.PROVIDER, provider);
        return current();
    }
}

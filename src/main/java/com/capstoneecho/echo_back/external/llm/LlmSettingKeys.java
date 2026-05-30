package com.capstoneecho.echo_back.external.llm;

// SettingsService 에 저장되는 LLM 관련 설정 키. yaml 기본값(app.llm.*)을 런타임에 덮어쓴다.
public final class LlmSettingKeys {

    // 활성 provider: "rule-based" | "gemini".
    public static final String PROVIDER = "llm.provider";

    // gemini 사용 시 호출할 모델 ID.
    public static final String GEMINI_MODEL = "llm.gemini.model";

    private LlmSettingKeys() {
    }
}

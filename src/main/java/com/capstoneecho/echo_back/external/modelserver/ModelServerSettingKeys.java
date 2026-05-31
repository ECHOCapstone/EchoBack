package com.capstoneecho.echo_back.external.modelserver;

// SettingsService 에 저장되는 모델 서버 관련 설정 키.
public final class ModelServerSettingKeys {

    // 음소인식에 사용할 모델 id (모델 서버 /models 의 후보 중 하나).
    // 비어 있으면 모델 서버의 기본 모델을 쓴다.
    public static final String MODEL = "modelserver.model";

    private ModelServerSettingKeys() {
    }
}

package com.capstoneecho.echo_back.external.modelserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog.ModelInfo;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// RecognitionModelPolicy 의 canonical 요구 판정. 안전 기본값은 "요구함" 이다.
class RecognitionModelPolicyTest {

    private final ModelServerClient modelServerClient = Mockito.mock(ModelServerClient.class);
    private final SettingsService settings = Mockito.mock(SettingsService.class);
    // 캐시 TTL 0 — 매 호출마다 최신 catalog 를 반영하도록.
    private final RecognitionModelPolicy policy =
            new RecognitionModelPolicy(modelServerClient, settings, 0L);

    private static ModelInfo model(String id, Boolean requiresCanonical) {
        return new ModelInfo(id, id, "echo", requiresCanonical);
    }

    @Test
    @DisplayName("설정 모델이 활성 baseline(requiresCanonical=false)이면 false 를 돌려준다")
    void activeBaselineDoesNotRequireCanonical() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("echo-baseline");
        when(modelServerClient.models()).thenReturn(new ModelCatalog(
                "echo-baseline", List.of(model("echo-baseline", false))));

        assertThat(policy.requiresCanonical()).isFalse();
    }

    @Test
    @DisplayName("설정 모델이 FiLM(requiresCanonical=true)이면 true 를 돌려준다")
    void filmRequiresCanonical() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("echo-film");
        when(modelServerClient.models()).thenReturn(new ModelCatalog(
                "echo-film", List.of(model("echo-film", true))));

        assertThat(policy.requiresCanonical()).isTrue();
    }

    @Test
    @DisplayName("설정 모델이 아직 비활성이라 requiresCanonical=null(미상)이면 안전하게 true 를 돌려준다")
    void unknownModelDefaultsToRequire() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("echo-film");
        when(modelServerClient.models()).thenReturn(new ModelCatalog(
                "echo-baseline",
                List.of(model("echo-baseline", false), model("echo-film", null))));

        assertThat(policy.requiresCanonical()).isTrue();
    }

    @Test
    @DisplayName("설정값이 비면 모델 서버의 활성 모델 판정을 따른다")
    void blankSettingFollowsActiveModel() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("");
        when(modelServerClient.models()).thenReturn(new ModelCatalog(
                "echo-baseline", List.of(model("echo-baseline", false))));

        assertThat(policy.requiresCanonical()).isFalse();
    }

    @Test
    @DisplayName("설정 모델 id 가 catalog 에 없으면 안전하게 true 를 돌려준다")
    void unknownIdDefaultsToRequire() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("ghost");
        when(modelServerClient.models()).thenReturn(new ModelCatalog(
                "echo-baseline", List.of(model("echo-baseline", false))));

        assertThat(policy.requiresCanonical()).isTrue();
    }

    @Test
    @DisplayName("모델 서버 장애로 catalog 를 못 읽으면 안전하게 true 를 돌려준다")
    void modelServerFailureDefaultsToRequire() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("echo-baseline");
        when(modelServerClient.models())
                .thenThrow(new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, "down"));

        assertThat(policy.requiresCanonical()).isTrue();
    }
}

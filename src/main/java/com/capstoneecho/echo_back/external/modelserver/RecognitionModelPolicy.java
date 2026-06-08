package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 현재 설정된 음소인식 모델이 추론에 canonical 음소열을 요구하는지 판정한다.
// 판정 출처는 모델 서버 /models(ModelCatalog) 의 requiresCanonical 이며, 매 채점마다 왕복하지 않도록
// 짧은 TTL 로 캐시한다.
//
// 안전 기본값은 "요구함(true)" 이다: canonical 을 넣어야 하는 FiLM 모델에 넣지 않으면 인식이 붕괴하지만,
// 필요 없는 모델에 canonical 을 함께 보내는 것은 무해하기 때문이다. 모델 서버는 활성 모델에 대해서만
// 확정값을 주고 비활성 모델은 null(미상) 을 주므로, 명시적으로 false 일 때만(활성 baseline·slplab)
// 병렬 최적화를 허용하고 미상/장애 시에는 주입을 택한다.
@Component
public class RecognitionModelPolicy {

    private final ModelServerClient modelServerClient;
    private final SettingsService settings;
    private final long cacheTtlMs;

    private volatile CachedCatalog cached;

    public RecognitionModelPolicy(
            ModelServerClient modelServerClient,
            SettingsService settings,
            @Value("${app.recognition.catalog-cache-ttl-ms:5000}") long cacheTtlMs) {
        this.modelServerClient = modelServerClient;
        this.settings = settings;
        this.cacheTtlMs = cacheTtlMs;
    }

    // 설정된(어드민이 고른) 모델이 canonical 주입을 요구하는가. 설정값이 비면 모델 서버의 활성 모델을 본다.
    public boolean requiresCanonical() {
        ModelCatalog catalog = catalog();
        if (catalog == null) {
            return true;
        }
        String configured = settings.getOrDefault(ModelServerSettingKeys.MODEL, "");
        String targetId = configured.isBlank() ? catalog.active() : configured;
        if (targetId == null) {
            return true;
        }
        return catalog.safeModels().stream()
                .filter(model -> targetId.equals(model.id()))
                .findFirst()
                .map(model -> model.requiresCanonical() == null || model.requiresCanonical())
                .orElse(true);
    }

    // 캐시가 비었거나 TTL 이 지났으면 /models 를 다시 읽는다. 모델 서버 장애 시 직전 캐시를 유지하고,
    // 캐시조차 없으면 null 을 돌려 호출자가 안전 기본값으로 떨어지게 한다.
    private ModelCatalog catalog() {
        CachedCatalog snapshot = cached;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - snapshot.fetchedAtMs() < cacheTtlMs) {
            return snapshot.catalog();
        }
        try {
            ModelCatalog fresh = modelServerClient.models();
            cached = new CachedCatalog(fresh, now);
            return fresh;
        } catch (BusinessException e) {
            return snapshot == null ? null : snapshot.catalog();
        }
    }

    private record CachedCatalog(ModelCatalog catalog, long fetchedAtMs) {}
}

package com.capstoneecho.echo_back.global.settings;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 런타임 설정의 단일 접근점. yaml 기본값 위에 DB 오버라이드를 얹어 노출한다.
// 시작 시 전체를 메모리 캐시에 적재하고 쓰기 시 write-through 한다.
// (단일 인스턴스 가정 — 다중 인스턴스로 확장하면 변경 전파 메커니즘이 필요하다.)
@Service
public class SettingsService {

    private final AppSettingRepository repository;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void loadCache() {
        repository.findAll().forEach(setting -> {
            if (setting.getSettingValue() != null) {
                cache.put(setting.getSettingKey(), setting.getSettingValue());
            }
        });
    }

    // DB 오버라이드가 있으면 그 값을, 없으면 호출자가 넘긴 기본값(보통 yaml)을 돌려준다.
    public String getOrDefault(String key, String defaultValue) {
        String override = cache.get(key);
        return override == null ? defaultValue : override;
    }

    // 오버라이드가 설정돼 있는지. 어드민 화면이 "기본값 사용 중 / 재정의됨" 을 구분할 때 쓴다.
    public Optional<String> getOverride(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Transactional
    public void set(String key, String value) {
        repository.findById(key).ifPresentOrElse(
                existing -> existing.updateValue(value),
                () -> repository.save(AppSetting.of(key, value)));
        cache.put(key, value);
    }
}

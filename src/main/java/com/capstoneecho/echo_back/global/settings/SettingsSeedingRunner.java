package com.capstoneecho.echo_back.global.settings;

import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import com.capstoneecho.echo_back.global.content.SeedFileLocations;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

// 부팅 시 settings-overrides.yaml 영구 저장본을 DB 오버라이드에 한 번 주입한다.
//   - DB 가 이미 오버라이드를 가지고 있으면 (운영자가 운영 중 직접 수정한 상태) 건너뛴다 (DB 우선).
//   - 영구 저장본이 없으면 yaml 공장 기본값만으로 동작한다.
// SettingsService.@PostConstruct 보다 늦게 실행되므로 캐시 갱신은 SettingsService.set() 의
// write-through 로 자연스럽게 이루어진다.
@Component
@Profile("!test")
public class SettingsSeedingRunner implements ApplicationRunner {

    private final AppSettingRepository repository;
    private final SettingsService settingsService;
    private final PersistableContentStore contentStore;

    public SettingsSeedingRunner(
            AppSettingRepository repository,
            SettingsService settingsService,
            PersistableContentStore contentStore
    ) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.contentStore = contentStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (!contentStore.exists(SeedFileLocations.SETTINGS_OVERRIDES)) {
            return;
        }
        readYaml().forEach((key, value) -> {
            if (key != null && value != null) {
                settingsService.set(key, value);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readYaml() {
        try (InputStream in = Files.newInputStream(
                contentStore.resolve(SeedFileLocations.SETTINGS_OVERRIDES))) {
            Object tree = new Yaml().load(in);
            if (tree instanceof Map<?, ?> map) {
                return (Map<String, String>) map;
            }
            return Map.of();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "설정 영구 저장본을 읽을 수 없습니다: " + SeedFileLocations.SETTINGS_OVERRIDES, e);
        }
    }
}

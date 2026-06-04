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

    // dumper 측이 모든 값을 quoted string 으로 출력하도록 보호되어 있지만, 손으로 yaml 을 편집한 경우
    // SnakeYAML 이 80 같은 unquoted scalar 를 Integer 로 파싱한다. Map<String,String> 에 그대로 캐스팅하면
    // forEach 에서 ClassCastException 이 나므로 키/값을 명시적으로 String.valueOf 로 변환한다.
    private Map<String, String> readYaml() {
        try (InputStream in = Files.newInputStream(
                contentStore.resolve(SeedFileLocations.SETTINGS_OVERRIDES))) {
            Object tree = new Yaml().load(in);
            if (!(tree instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, String> result = new java.util.LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                result.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "설정 영구 저장본을 읽을 수 없습니다: " + SeedFileLocations.SETTINGS_OVERRIDES, e);
        }
    }
}

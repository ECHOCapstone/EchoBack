package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import com.capstoneecho.echo_back.global.content.PersistableSeed;
import com.capstoneecho.echo_back.global.content.SeedFileLocations;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

// 설정 오버라이드(DB app_setting) 의 영구 저장과 시드 재적용.
//   - 영구 저장: 현재 DB 의 오버라이드를 settings-overrides.yaml 로 직렬화한다. 새 인스턴스가
//                부팅하거나 DB 를 비우고 다시 시드해도 같은 상태가 복원된다.
//   - 시드 재적용: DB 오버라이드를 모두 비우고 영구 저장본 파일도 지운다. 이후 호출은 yaml
//                  공장 기본값으로 동작한다 (SettingsSeedingRunner 가 영구 저장본을 다시 적용).
@Service
public class SettingsPersistService implements PersistableSeed {

    private static final String DOMAIN = "settings";

    private final SettingsService settingsService;
    private final PersistableContentStore contentStore;

    public SettingsPersistService(
            SettingsService settingsService,
            PersistableContentStore contentStore
    ) {
        this.settingsService = settingsService;
        this.contentStore = contentStore;
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public boolean supportsExplicitPersist() {
        return true;
    }

    @Override
    public void persistCurrentStateToFile() {
        Map<String, String> snapshot = settingsService.snapshotOverrides();
        contentStore.writeText(SeedFileLocations.SETTINGS_OVERRIDES, toYaml(snapshot));
    }

    @Override
    public void resetToDefaults() {
        settingsService.clearAll();
        contentStore.delete(SeedFileLocations.SETTINGS_OVERRIDES);
    }

    @Override
    public SeedFileStatus fileStatus() {
        return contentStore.statusOf(SeedFileLocations.SETTINGS_OVERRIDES);
    }

    private String toYaml(Map<String, String> snapshot) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        // 모든 scalar 를 double-quoted 로 출력 — "80" 처럼 숫자처럼 보이는 문자열이 unquoted 로 dump 되어
        // 다음 부팅 때 reader 가 Integer 로 파싱하면서 ClassCast 가 나는 비대칭을 막는다.
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        return new Yaml(options).dump(snapshot);
    }
}

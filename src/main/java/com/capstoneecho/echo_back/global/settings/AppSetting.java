package com.capstoneecho.echo_back.global.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 키-값 설정 오버라이드 한 건. yaml 기본값 위에 덮어쓰는 런타임 값을 보관한다.
@Entity
@Table(name = "app_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private AppSetting(String settingKey, String settingValue) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedAt = Instant.now();
    }

    public static AppSetting of(String settingKey, String settingValue) {
        if (settingKey == null || settingKey.isBlank()) {
            throw new IllegalArgumentException("settingKey is required");
        }
        return new AppSetting(settingKey, settingValue);
    }

    public void updateValue(String settingValue) {
        this.settingValue = settingValue;
        this.updatedAt = Instant.now();
    }
}

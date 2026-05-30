package com.capstoneecho.echo_back.global.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class SettingsServiceTest {

    @Autowired
    private AppSettingRepository repository;

    private SettingsService settings;

    @BeforeEach
    void setUp() {
        settings = new SettingsService(repository);
        settings.loadCache();
    }

    @Test
    @DisplayName("오버라이드가 없으면 기본값을 돌려준다")
    void returnsDefaultWhenAbsent() {
        assertThat(settings.getOrDefault("llm.provider", "rule-based")).isEqualTo("rule-based");
        assertThat(settings.getOverride("llm.provider")).isEmpty();
    }

    @Test
    @DisplayName("set 후에는 오버라이드 값을 돌려준다")
    void returnsOverrideAfterSet() {
        settings.set("llm.provider", "gemini");

        assertThat(settings.getOrDefault("llm.provider", "rule-based")).isEqualTo("gemini");
        assertThat(settings.getOverride("llm.provider")).contains("gemini");
        assertThat(repository.findById("llm.provider")).isPresent();
    }

    @Test
    @DisplayName("같은 키에 다시 set 하면 값이 갱신된다")
    void updatesExistingValue() {
        settings.set("llm.gemini.model", "gemini-2.0-flash");
        settings.set("llm.gemini.model", "gemini-2.5-flash");

        assertThat(settings.getOrDefault("llm.gemini.model", "x")).isEqualTo("gemini-2.5-flash");
        assertThat(repository.count()).isEqualTo(1);
    }
}

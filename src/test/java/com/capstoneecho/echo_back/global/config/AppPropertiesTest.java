package com.capstoneecho.echo_back.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("AppProperties binds every app.* group from application-test.yaml")
    void bindsAllGroups() {
        assertThat(appProperties.jwt().secret()).isNotBlank();
        assertThat(appProperties.jwt().expirationMs()).isPositive();

        assertThat(appProperties.cors().allowedOrigins()).isNotEmpty();
        assertThat(appProperties.cors().allowedMethods()).contains("GET", "POST");
        assertThat(appProperties.cors().exposedHeaders()).contains("Authorization");
        assertThat(appProperties.cors().allowCredentials()).isTrue();
        assertThat(appProperties.cors().maxAgeSec()).isPositive();

        assertThat(appProperties.modelServer().baseUrl()).startsWith("http");
        assertThat(appProperties.modelServer().timeoutMs()).isPositive();

        assertThat(appProperties.llm().provider()).isEqualTo("rule-based");
        assertThat(appProperties.storage().localRoot()).isNotBlank();
        assertThat(appProperties.tts().provider()).isEqualTo("local-stub");
        assertThat(appProperties.stats().zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("test profile loads H2 in-memory datasource in MySQL compatibility mode")
    void testProfileLoadsH2Datasource() throws Exception {
        String configuredUrl = environment.getProperty("spring.datasource.url");
        assertThat(configuredUrl)
            .contains("h2:mem:testdb")
            .containsIgnoringCase("MODE=MySQL");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualToIgnoringCase("MySQL");
        }
    }
}

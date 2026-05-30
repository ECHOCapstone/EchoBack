package com.capstoneecho.echo_back.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("prod")
class ProdProfileSmokeTest {

    @DynamicPropertySource
    static void stubProdEnvironment(DynamicPropertyRegistry registry) {
        // application-prod.yaml 의 ${...} 자리를 부트업이 가능한 더미 값으로 채운다.
        registry.add("JWT_SECRET",
                () -> "prod-smoke-test-jwt-secret-must-be-long-and-random-1234567890");
        registry.add("MODEL_SERVER_BASE_URL", () -> "http://stub.model.invalid:8000");
        // 실제 MySQL 없이 부트만 검증하므로 H2 (MySQL 호환 모드) 로 datasource override.
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:prod-smoke;MODE=MySQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.H2Dialect");
        // H2 부트 검증용이므로 MySQL 마이그레이션(Flyway) 은 끄고 Hibernate 가 스키마를 만든다.
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("prod 프로파일이 필수 env 스텁만으로 컨텍스트 부트업한다")
    void bootsWithProdProfileAndEnvVars() {
        // JWT_SECRET → app.jwt.secret
        assertThat(appProperties.jwt().secret())
                .as("JWT_SECRET 가 app.jwt.secret 에 바인딩")
                .contains("prod-smoke-test-jwt-secret");
        assertThat(appProperties.jwt().expirationMs()).isPositive();

        // MODEL_SERVER_BASE_URL → app.model-server.base-url
        assertThat(appProperties.modelServer().baseUrl())
                .as("MODEL_SERVER_BASE_URL 가 app.model-server.base-url 에 바인딩")
                .isEqualTo("http://stub.model.invalid:8000");

        // stats zone 은 운영에서도 KST 고정
        assertThat(appProperties.stats().zone()).isEqualTo("Asia/Seoul");

        // jackson time-zone UTC (운영 로그 UTC 정책)
        assertThat(environment.getProperty("spring.jackson.time-zone")).isEqualTo("UTC");

        // multipart 25MB
        assertThat(environment.getProperty("spring.servlet.multipart.max-file-size"))
                .isEqualTo("25MB");
        assertThat(environment.getProperty("spring.servlet.multipart.max-request-size"))
                .isEqualTo("25MB");

        // logging 레벨
        assertThat(environment.getProperty("logging.level.root")).isEqualToIgnoringCase("INFO");
        assertThat(environment.getProperty("logging.level.com.capstoneecho"))
                .isEqualToIgnoringCase("INFO");

        // HikariCP 풀 설정
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size",
                Integer.class)).isEqualTo(20);
        assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout",
                Long.class)).isEqualTo(30_000L);

        // Actuator exposure: health, info 만
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");

        // 운영에서 stacktrace 노출 금지
        assertThat(environment.getProperty("spring.web.error.include-stacktrace"))
                .isEqualToIgnoringCase("never");
    }
}

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

        // OAuth2 client registration 스텁 — prod 프로파일은 GOOGLE_CLIENT_ID/KAKAO_REST_API_KEY 등
        // env 가 없으면 client-id 가 빈 문자열이라 컨텍스트 부트업이 실패한다. test 프로파일과
        // 동일한 더미 자격증명으로 채운다.
        registry.add("spring.security.oauth2.client.registration.google.client-id",
                () -> "test-google-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret",
                () -> "test-google-client-secret");
        registry.add("spring.security.oauth2.client.registration.kakao.client-id",
                () -> "test-kakao-client-id");
        registry.add("spring.security.oauth2.client.registration.kakao.client-secret",
                () -> "test-kakao-client-secret");
        // kakao registration 을 issuer-uri 없는 stub provider 로 재지정한다.
        // base application.yaml 의 provider.kakao.issuer-uri(https://kauth.kakao.com)는
        // DynamicPropertySource 로 제거할 수 없고, 빈 문자열로 덮으면 "issuer cannot be empty"
        // 가 난다(Spring 은 빈 issuer 도 discovery 시도). registration 이 다른 provider 를
        // 가리키게 하면 base issuer-uri 가 무시돼 OIDC discovery 네트워크 호출 없이 부트업한다.
        registry.add("spring.security.oauth2.client.registration.kakao.provider",
                () -> "kakao-stub");
        registry.add("spring.security.oauth2.client.provider.kakao-stub.authorization-uri",
                () -> "https://kauth.kakao.com/oauth/authorize");
        registry.add("spring.security.oauth2.client.provider.kakao-stub.token-uri",
                () -> "https://kauth.kakao.com/oauth/token");
        registry.add("spring.security.oauth2.client.provider.kakao-stub.user-info-uri",
                () -> "https://kapi.kakao.com/v1/oidc/userinfo");
        registry.add("spring.security.oauth2.client.provider.kakao-stub.user-name-attribute",
                () -> "sub");
        registry.add("spring.security.oauth2.client.provider.kakao-stub.jwk-set-uri",
                () -> "https://kauth.kakao.com/.well-known/jwks.json");
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

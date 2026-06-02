package com.capstoneecho.echo_back.global.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ActuatorAccessTest {

    @DynamicPropertySource
    static void stubProdEnvironment(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET",
                () -> "actuator-access-test-jwt-secret-must-be-long-and-random-1234567890");
        registry.add("MODEL_SERVER_BASE_URL", () -> "http://stub.model.invalid:8000");
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:actuator-access;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    private MockMvc mockMvc;

    @Test
    @DisplayName("/actuator/health 는 운영에서도 permitAll — 미인증 200 OK")
    void actuatorHealthIsPermitAllInProd() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/info 는 ROLE_ADMIN 필요 — 미인증 401")
    void actuatorMetricsRequiresAdminInProd() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/info 는 ROLE_ADMIN 인증 시 접근 가능")
    void actuatorInfoAccessibleAsAdmin() throws Exception {
        mockMvc.perform(get("/actuator/info").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}

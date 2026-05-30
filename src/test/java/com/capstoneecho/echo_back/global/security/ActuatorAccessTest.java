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

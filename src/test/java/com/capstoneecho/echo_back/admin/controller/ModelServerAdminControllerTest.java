package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ModelServerAdminControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private JwtProvider jwtProvider;

    private String adminToken() {
        return jwtProvider.issue(1L,
                Map.of("username", "admin", "email", "admin@test.com", "role", "ADMIN"));
    }

    @Test
    @DisplayName("GET /api/admin/model-server → 선택값 + 후보 + serverAvailable")
    void getConfig() throws Exception {
        // 테스트엔 모델 서버가 떠 있지 않을 수 있으므로 값 자체가 아닌 응답 형태를 검증한다.
        mockMvc.perform(get("/api/admin/model-server").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selected").exists())
                .andExpect(jsonPath("$.data.options").isArray())
                .andExpect(jsonPath("$.data.serverAvailable").isBoolean());
    }

    @Test
    @DisplayName("USER 권한 → 403")
    void userForbidden() throws Exception {
        String userToken = jwtProvider.issue(2L,
                Map.of("username", "u", "email", "u@test.com", "role", "USER"));

        mockMvc.perform(get("/api/admin/model-server").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}

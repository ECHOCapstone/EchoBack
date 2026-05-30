package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingsAdminControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private JwtProvider jwtProvider;

    private String adminToken() {
        return jwtProvider.issue(1L,
                Map.of("username", "admin", "email", "admin@test.com", "role", "ADMIN"));
    }

    @Test
    @DisplayName("GET /api/admin/settings → 편집 가능한 설정 목록")
    void listSettings() throws Exception {
        mockMvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.key == 'gamification.passThreshold')]").exists());
    }

    @Test
    @DisplayName("PUT 으로 재정의 후 DELETE 로 기본값 복원 (공유 캐시 오염 방지)")
    void overrideThenReset() throws Exception {
        try {
            mockMvc.perform(put("/api/admin/settings/gamification.passThreshold")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(APPLICATION_JSON)
                            .content("{\"value\": \"90\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.overridden").value(true))
                    .andExpect(jsonPath("$.data.value").value("90.0"));
        } finally {
            mockMvc.perform(delete("/api/admin/settings/gamification.passThreshold")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.overridden").value(false));
        }
    }

    @Test
    @DisplayName("형식에 맞지 않는 값 → 400")
    void rejectsInvalidValue() throws Exception {
        mockMvc.perform(put("/api/admin/settings/gamification.completionExp")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"value\": \"열\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("알 수 없는 키 → 400")
    void rejectsUnknownKey() throws Exception {
        mockMvc.perform(put("/api/admin/settings/gamification.unknown")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"value\": \"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("USER 권한 → 403")
    void userForbidden() throws Exception {
        String userToken = jwtProvider.issue(2L,
                Map.of("username", "u", "email", "u@test.com", "role", "USER"));

        mockMvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}

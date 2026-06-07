package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.support.AbstractAdminControllerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LlmAdminControllerTest extends AbstractAdminControllerIntegrationTest {

    @Test
    @DisplayName("GET /api/admin/llm ADMIN → 현재 설정 + 후보 목록")
    void getReturnsConfig() throws Exception {
        mockMvc.perform(get("/api/admin/llm").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provider").exists())
                .andExpect(jsonPath("$.data.providerOptions").isArray())
                .andExpect(jsonPath("$.data.modelOptions").isArray());
    }

    @Test
    @DisplayName("PUT /api/admin/llm provider=rule-based → 200 + 적용값 반영")
    void putRuleBasedApplies() throws Exception {
        mockMvc.perform(put("/api/admin/llm")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider\": \"rule-based\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("rule-based"));
    }

    @Test
    @DisplayName("PUT /api/admin/llm 지원하지 않는 provider → 400")
    void putUnknownProviderRejected() throws Exception {
        mockMvc.perform(put("/api/admin/llm")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider\": \"openai\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/admin/llm USER 권한 → 403")
    void userRoleForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/llm").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }
}

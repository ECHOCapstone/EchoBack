package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class PromptAdminControllerTest extends AbstractControllerIntegrationTest {

    // 매 실행마다 새 임시 디렉터리를 편집본 위치로 둬, 이전 실행의 편집본 파일에 오염되지 않게 한다.
    private static final String CONTENT_DIR;

    static {
        try {
            CONTENT_DIR = Files.createTempDirectory("echo-prompt-test").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void contentDir(DynamicPropertyRegistry registry) {
        registry.add("app.content.dir", () -> CONTENT_DIR);
    }

    @Autowired
    private JwtProvider jwtProvider;

    private String adminToken() {
        return jwtProvider.issue(1L,
                Map.of("username", "admin", "email", "admin@test.com", "role", "ADMIN"));
    }

    @Test
    @DisplayName("GET /api/admin/prompts → 프롬프트 목록")
    void listPrompts() throws Exception {
        mockMvc.perform(get("/api/admin/prompts").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.key == 'system')]").exists());
    }

    @Test
    @DisplayName("GET /api/admin/prompts/{key} → 기본 본문, overridden=false")
    void getPrompt() throws Exception {
        mockMvc.perform(get("/api/admin/prompts/system").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("system"))
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.overridden").value(false));
    }

    @Test
    @DisplayName("PUT 으로 재정의 후 DELETE 로 기본값 복원")
    void overrideThenReset() throws Exception {
        // 재정의: overridden=true, 내용 반영.
        mockMvc.perform(put("/api/admin/prompts/system")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\": \"테스트 재정의 본문\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overridden").value(true))
                .andExpect(jsonPath("$.data.content").value("테스트 재정의 본문"));

        // 초기화: 기본값으로 복원 (다른 테스트 오염 방지).
        mockMvc.perform(delete("/api/admin/prompts/system")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overridden").value(false))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("아학편")));
    }

    @Test
    @DisplayName("없는 키 → 404 PROMPT_NOT_FOUND")
    void unknownKeyReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/prompts/does-not-exist")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROMPT_NOT_FOUND"));
    }

    @Test
    @DisplayName("USER 권한 → 403")
    void userForbidden() throws Exception {
        String userToken = jwtProvider.issue(2L,
                Map.of("username", "u", "email", "u@test.com", "role", "USER"));

        mockMvc.perform(get("/api/admin/prompts").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}

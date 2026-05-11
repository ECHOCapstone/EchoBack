package com.capstoneecho.echo_back.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("GET /api/members/me → 200 + 프로필 봉투 + REST Docs 스니펫")
    void getMeReturnsProfile() throws Exception {
        User saved = userRepository.save(User.signup(
                "dave", "dave@test.com", passwordEncoder.encode("Password1!"), "Dave"));
        String token = issueToken(saved);

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.username").value("dave"))
                .andExpect(jsonPath("$.data.email").value("dave@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("Dave"))
                .andDo(document("members/get-me"));

        assertSnippetCreated("members/get-me");
    }

    @Test
    @DisplayName("GET /api/members/me 토큰 없음 → 401 UNAUTHORIZED 봉투")
    void getMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/members/me 토큰의 userId 가 미존재 → 404 USER_NOT_FOUND")
    void getMeWithMissingUserReturns404() throws Exception {
        String token = jwtProvider.issue(999_999L,
                Map.of("username", "ghost", "email", "ghost@test.com"));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/members/me/nickname → 200 + 갱신된 프로필 + REST Docs 스니펫")
    void patchNicknameUpdatesValue() throws Exception {
        User saved = userRepository.save(User.signup(
                "erin", "erin@test.com", passwordEncoder.encode("Password1!"), "Erin"));
        String token = issueToken(saved);

        String body = """
                {"nickname": "ErinNew"}
                """;

        mockMvc.perform(patch("/api/members/me/nickname")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("ErinNew"))
                .andDo(document("members/patch-me-nickname"));

        assertSnippetCreated("members/patch-me-nickname");
    }

    @Test
    @DisplayName("PATCH /api/members/me/nickname 공백 nickname → 400 VALIDATION_FAILED")
    void patchNicknameBlankReturns400Validation() throws Exception {
        User saved = userRepository.save(User.signup(
                "frank", "frank@test.com", passwordEncoder.encode("Password1!"), "Frank"));
        String token = issueToken(saved);

        String body = """
                {"nickname": "   "}
                """;

        mockMvc.perform(patch("/api/members/me/nickname")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private String issueToken(User user) {
        return jwtProvider.issue(
                user.getId(),
                Map.of("username", user.getUsername(), "email", user.getEmail()));
    }

    private static void assertSnippetCreated(String snippetId) {
        File dir = new File("build/generated-snippets/" + snippetId);
        assertThat(dir).as("REST Docs snippet dir must exist: %s", dir).exists();
        assertThat(dir.list())
                .as("expected REST Docs snippet files in %s", dir)
                .isNotNull()
                .isNotEmpty();
    }
}

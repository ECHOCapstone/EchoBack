package com.capstoneecho.echo_back.learning.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SessionControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private SentenceSplitter sentenceSplitter;

    @Test
    @DisplayName("GET /api/sessions → 200 + 본인 세션만 + REST Docs 스니펫")
    void listReturnsOwnSessionsOnly() throws Exception {
        User owner = savedUser("sessuser1", "sessuser1@test.com");
        User other = savedUser("sessother1", "sessother1@test.com");
        sessionRepository.save(Session.create(owner, "My First"));
        sessionRepository.save(Session.create(owner, "My Second"));
        sessionRepository.save(Session.create(other, "Other's Session"));

        String token = issueToken(owner);

        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].title",
                        Matchers.everyItem(Matchers.not(Matchers.equalTo("Other's Session")))))
                .andDo(document("sessions/list"));

        assertSnippetCreated("sessions/list");
    }

    @Test
    @DisplayName("POST /api/sessions → 201 + SessionDetailResponse + REST Docs 스니펫")
    void createReturns201AndDetail() throws Exception {
        User user = savedUser("sessuser2", "sessuser2@test.com");
        String token = issueToken(user);

        String body = """
                { "title": "내 발음 연습 5/5" }
                """;

        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("내 발음 연습 5/5"))
                .andExpect(jsonPath("$.data.favorite").value(false))
                .andExpect(jsonPath("$.data.scriptText").value(""))
                .andExpect(jsonPath("$.data.sentences").isArray())
                .andExpect(jsonPath("$.data.sentences.length()").value(0))
                .andDo(document("sessions/create"));

        assertSnippetCreated("sessions/create");
    }

    @Test
    @DisplayName("GET /api/sessions/{id} → 200 + 상세 + REST Docs 스니펫")
    void getReturnsDetail() throws Exception {
        User user = savedUser("sessuser3", "sessuser3@test.com");
        Session session = Session.create(user, "Original");
        session.updateScript("Hello world. This is fun.", sentenceSplitter);
        Session saved = sessionRepository.save(session);

        String token = issueToken(user);

        mockMvc.perform(get("/api/sessions/{sessionId}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.title").value("Original"))
                .andExpect(jsonPath("$.data.scriptText").value("Hello world. This is fun."))
                .andExpect(jsonPath("$.data.sentences.length()").value(2))
                .andDo(document("sessions/get"));

        assertSnippetCreated("sessions/get");
    }

    @Test
    @DisplayName("GET /api/sessions/{id} 미존재 → 404 SESSION_NOT_FOUND")
    void getUnknownSessionReturns404() throws Exception {
        User user = savedUser("sessuser4", "sessuser4@test.com");
        String token = issueToken(user);

        mockMvc.perform(get("/api/sessions/{sessionId}", 999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} 부분 수정 → 200 + 변경된 필드만 반영 + REST Docs 스니펫")
    void patchPartialFieldsOnly() throws Exception {
        User user = savedUser("sessuser5", "sessuser5@test.com");
        Session session = Session.create(user, "Original Title");
        session.updateScript("Initial text.", sentenceSplitter);
        Session saved = sessionRepository.save(session);

        String token = issueToken(user);

        String body = """
                { "favorite": true }
                """;

        mockMvc.perform(patch("/api/sessions/{sessionId}", saved.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.title").value("Original Title"))
                .andExpect(jsonPath("$.data.favorite").value(true))
                .andExpect(jsonPath("$.data.scriptText").value("Initial text."))
                .andDo(document("sessions/patch"));

        assertSnippetCreated("sessions/patch");
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} title 너무 김 → 400 VALIDATION_FAILED")
    void patchInvalidTitleReturns400() throws Exception {
        User user = savedUser("sessuser6", "sessuser6@test.com");
        Session saved = sessionRepository.save(Session.create(user, "ok"));

        String token = issueToken(user);

        String body = "{\"title\": \"" + "a".repeat(101) + "\"}";

        mockMvc.perform(patch("/api/sessions/{sessionId}", saved.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} 미존재 → 404 SESSION_NOT_FOUND")
    void patchUnknownSessionReturns404() throws Exception {
        User user = savedUser("sessuser7", "sessuser7@test.com");
        String token = issueToken(user);

        mockMvc.perform(patch("/api/sessions/{sessionId}", 999_999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"favorite\": true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/sessions/{id} → 200 + {success:true} + 영속 제거 + REST Docs 스니펫")
    void deleteReturns200AndRemoves() throws Exception {
        User user = savedUser("sessuser8", "sessuser8@test.com");
        Session saved = sessionRepository.save(Session.create(user, "To delete"));

        String token = issueToken(user);

        mockMvc.perform(delete("/api/sessions/{sessionId}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("sessions/delete"));

        assertSnippetCreated("sessions/delete");
        assertThat(sessionRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/sessions/{id} 타사용자 세션 → 404 SESSION_NOT_FOUND")
    void deleteOtherUsersSessionReturns404() throws Exception {
        User owner = savedUser("sessowner", "sessowner@test.com");
        User attacker = savedUser("sessattacker", "sessattacker@test.com");
        Session saved = sessionRepository.save(Session.create(owner, "Owner's session"));

        String token = issueToken(attacker);

        mockMvc.perform(delete("/api/sessions/{sessionId}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));

        assertThat(sessionRepository.findById(saved.getId()))
                .as("타사용자 세션은 절대 삭제되면 안 됨")
                .isPresent();
    }

    @Test
    @DisplayName("GET /api/sessions 토큰 없음 → 401 UNAUTHORIZED")
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    private User savedUser(String username, String email) {
        return userRepository.save(User.signup(
                username, email, passwordEncoder.encode("Password1!"), "Nick"));
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

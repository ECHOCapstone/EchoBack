package com.capstoneecho.echo_back.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.member.service.AuthService;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AuthControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("POST /api/auth/signup → 201 + AuthTokenResponse 봉투 + REST Docs 스니펫")
    void signupReturns201AndToken() throws Exception {
        String body = """
                {
                  "username": "alice",
                  "email": "alice@test.com",
                  "password": "Password1!",
                  "nickname": "Alice"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.profile.username").value("alice"))
                .andExpect(jsonPath("$.data.profile.email").value("alice@test.com"))
                .andExpect(jsonPath("$.data.profile.nickname").value("Alice"))
                .andDo(document("auth/post-signup"));

        assertSnippetCreated("auth/post-signup");
    }

    @Test
    @DisplayName("POST /api/auth/signup 동일 username 재가입 → 409 USERNAME_DUPLICATED")
    void signupDuplicateUsernameReturns409() throws Exception {
        userRepository.save(User.signup(
                "alice", "alice-existing@test.com", passwordEncoder.encode("Password1!"), "Alice"));

        String body = """
                {
                  "username": "alice",
                  "email": "alice-new@test.com",
                  "password": "Password1!",
                  "nickname": "Alice2"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("USERNAME_DUPLICATED"));
    }

    @Test
    @DisplayName("POST /api/auth/signup 동일 email → 409 EMAIL_DUPLICATED")
    void signupDuplicateEmailReturns409() throws Exception {
        userRepository.save(User.signup(
                "existing", "dup@test.com", passwordEncoder.encode("Password1!"), "Existing"));

        String body = """
                {
                  "username": "newuser",
                  "email": "dup@test.com",
                  "password": "Password1!",
                  "nickname": "NewUser"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("POST /api/auth/login → 200 + AuthTokenResponse 봉투 + REST Docs 스니펫")
    void loginReturns200AndToken() throws Exception {
        userRepository.save(User.signup(
                "bob", "bob@test.com", passwordEncoder.encode("Password1!"), "Bob"));

        String body = """
                {
                  "usernameOrEmail": "bob",
                  "password": "Password1!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.profile.username").value("bob"))
                .andDo(document("auth/post-login"));

        assertSnippetCreated("auth/post-login");
    }

    @Test
    @DisplayName("POST /api/auth/login 잘못된 비밀번호 → 401 LOGIN_FAILED")
    void loginWithBadCredentialsReturns401LoginFailed() throws Exception {
        userRepository.save(User.signup(
                "carol", "carol@test.com", passwordEncoder.encode("Password1!"), "Carol"));

        String body = """
                {
                  "usernameOrEmail": "carol",
                  "password": "WrongPass1!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/check-username → 200 + {available} 봉투 + REST Docs 스니펫")
    void checkUsernameReturns200AndEnvelope() throws Exception {
        String body = """
                {"username": "freshname"}
                """;

        mockMvc.perform(post("/api/auth/check-username")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andDo(document("auth/post-check-username"));

        assertSnippetCreated("auth/post-check-username");
    }

    @Test
    @DisplayName("POST /api/auth/check-email 이미 사용중 → available=false + REST Docs 스니펫")
    void checkEmailReturns200AndEnvelope() throws Exception {
        userRepository.save(User.signup(
                "taken", "taken@test.com", passwordEncoder.encode("Password1!"), "Taken"));

        String body = """
                {"email": "taken@test.com"}
                """;

        mockMvc.perform(post("/api/auth/check-email")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(false))
                .andDo(document("auth/post-check-email"));

        assertSnippetCreated("auth/post-check-email");
    }

    @Test
    @DisplayName("GET /api/auth/oauth2/google/demo → 200 + AuthTokenResponse, 멱등 + REST Docs 스니펫")
    void oauth2GoogleDemoIsIdempotent() throws Exception {
        mockMvc.perform(get("/api/auth/oauth2/google/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.profile.email").value(AuthService.DEMO_GOOGLE_EMAIL))
                .andDo(document("auth/get-oauth2-google-demo"));

        mockMvc.perform(get("/api/auth/oauth2/google/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.email").value(AuthService.DEMO_GOOGLE_EMAIL));

        assertSnippetCreated("auth/get-oauth2-google-demo");
        assertThat(userRepository.findByEmail(AuthService.DEMO_GOOGLE_EMAIL))
                .as("demo google account exists exactly once").isPresent();
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

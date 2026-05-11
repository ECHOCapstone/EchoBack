package com.capstoneecho.echo_back.statistics.stats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.support.TransactionTemplate;

class StatsControllerTest extends AbstractControllerIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("GET /api/stats/me?year=&month= → 200 + StatsResponse + REST Docs 스니펫")
    void getMyStatsReturnsResponse() throws Exception {
        User user = newUser("statsme");
        String token = issueToken(user);

        mockMvc.perform(get("/api/stats/me")
                        .param("year", "2026")
                        .param("month", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.streak").value(0))
                .andExpect(jsonPath("$.data.exp").value(0))
                .andExpect(jsonPath("$.data.attendance.year").value(2026))
                .andExpect(jsonPath("$.data.attendance.month").value(5))
                .andExpect(jsonPath("$.data.badges[?(@.id == 'FIRST_FEEDBACK')]").exists())
                .andDo(document("stats/me"));

        assertSnippetCreated("stats/me");
    }

    @Test
    @DisplayName("GET /api/stats/me (year/month 생략) → 200 + 현재 KST 기준 attendance")
    void getMyStatsWithDefaultsReturnsResponse() throws Exception {
        User user = newUser("statsdefault");
        String token = issueToken(user);

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attendance.year").isNumber())
                .andExpect(jsonPath("$.data.attendance.month").isNumber());
    }

    @Test
    @DisplayName("GET /api/stats/me?month=13 → 400 VALIDATION_FAILED")
    void invalidMonthReturns400Validation() throws Exception {
        User user = newUser("statsbad");
        String token = issueToken(user);

        mockMvc.perform(get("/api/stats/me")
                        .param("year", "2026")
                        .param("month", "13")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/stats/me 인증 누락 → 401 UNAUTHORIZED")
    void missingAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/stats/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // ---------- helpers ----------

    private User newUser(String localPart) {
        return transactionTemplate.execute(s -> userRepository.save(User.signup(
                localPart + System.nanoTime(),
                localPart + "-" + System.nanoTime() + "@test.com",
                passwordEncoder.encode("Password1!"),
                "Nick")));
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

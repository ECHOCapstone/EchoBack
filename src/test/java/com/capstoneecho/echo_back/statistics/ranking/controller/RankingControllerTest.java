package com.capstoneecho.echo_back.statistics.ranking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import java.time.Instant;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

class RankingControllerTest extends AbstractControllerIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private SettingsService settingsService;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetState() {
        transactionTemplate.executeWithoutResult(s -> feedbackRepository.deleteAll());
        settingsService.set(RuntimeSettings.WEEKLY_WINDOW_DAYS, "7");
        settingsService.set(RuntimeSettings.WEEKLY_TOP_N, "5");
        settingsService.set(RuntimeSettings.RANKING_MIN_ACTIVITY_COUNT, "3");
    }

    @Test
    @DisplayName("GET /api/ranking/weekly (활동 없음) → 빈 entries + myRank=0 + minActivityCount 노출")
    void weeklyWithoutActivityReturnsEmptyEntries() throws Exception {
        User user = newUser("empty");
        String token = issueToken(user);

        mockMvc.perform(get("/api/ranking/weekly")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entries").isEmpty())
                .andExpect(jsonPath("$.data.myRank").value(0))
                .andExpect(jsonPath("$.data.myActivityCount").value(0))
                .andExpect(jsonPath("$.data.myEntryShown").value(false))
                .andExpect(jsonPath("$.data.windowDays").value(7))
                .andExpect(jsonPath("$.data.minActivityCount").value(3))
                .andExpect(jsonPath("$.data.period").isNotEmpty())
                .andDo(document("ranking/weekly"));

        assertSnippetCreated("ranking/weekly");
    }

    @Test
    @DisplayName("GET /api/ranking/weekly (임계 통과) → 본인 entry 포함 + myEntryShown=true")
    void weeklyWithQualifyingActivityIncludesSelf() throws Exception {
        User user = newUser("qualify");
        Script script = seedScript("qualify");
        Instant now = Instant.now();
        completeFeedbackAt(user, script, "S1", 80.0, now);
        completeFeedbackAt(user, script, "S2", 90.0, now);
        completeFeedbackAt(user, script, "S3", 100.0, now);
        String token = issueToken(user);

        mockMvc.perform(get("/api/ranking/weekly")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.myRank").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.myAccuracy").value(90.0))
                .andExpect(jsonPath("$.data.myActivityCount").value(3))
                .andExpect(jsonPath("$.data.myEntryShown").value(true))
                .andExpect(jsonPath("$.data.entries[?(@.isMe == true)]").exists())
                .andExpect(jsonPath("$.data.entries[0].activityCount").value(3));
    }

    @Test
    @DisplayName("GET /api/ranking/weekly 인증 누락 → 401 UNAUTHORIZED")
    void missingAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/ranking/weekly"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private User newUser(String localPart) {
        return transactionTemplate.execute(s -> userRepository.save(User.signup(
                localPart + System.nanoTime(),
                localPart + "-" + System.nanoTime() + "@test.com",
                passwordEncoder.encode("Password1!"),
                "Nick" + localPart)));
    }

    private String issueToken(User user) {
        return jwtProvider.issue(
                user.getId(),
                Map.of("username", user.getUsername(), "email", user.getEmail()));
    }

    private Script seedScript(String suffix) {
        return transactionTemplate.execute(s -> {
            Track track = trackRepository.save(
                    Track.create("T-rk-" + suffix + "-" + System.nanoTime(), "d", 1));
            return scriptRepository.save(
                    Script.createChapter(track, 1, "Script-" + suffix,
                            "content", Difficulty.EASY, null, null));
        });
    }

    private void completeFeedbackAt(User user, Script script, String title, double accuracy, Instant completedAt) {
        Long fbId = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, title, accuracy, "TH", "think", "g")).getId());
        int affected = transactionTemplate.execute(s ->
                feedbackRepository.markCompletedAtomically(fbId, user.getId(), completedAt));
        assertThat(affected).isEqualTo(1);
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

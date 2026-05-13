package com.capstoneecho.echo_back.statistics.ranking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
import com.capstoneecho.echo_back.statistics.ranking.repository.DemoRankingEntryRepository;
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
    @Autowired private DemoRankingEntryRepository demoRankingEntryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seedDemoRanking() {
        transactionTemplate.executeWithoutResult(s -> {
            feedbackRepository.deleteAll();
            demoRankingEntryRepository.deleteAll();
            demoRankingEntryRepository.save(DemoRankingEntry.create("Aaron", 95));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Brenda", 88));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Cody", 81));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Dana", 74));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Erin", 67));
        });
    }

    @Test
    @DisplayName("GET /api/ranking/today (오늘 피드백 없음) → demo 시드 정렬 + 본인은 unranked")
    void todayWithoutOwnFeedbackReturnsDemoSeedAndUnranked() throws Exception {
        User user = newUser("rkunranked");
        String token = issueToken(user);

        mockMvc.perform(get("/api/ranking/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.myRank").value(0))
                .andExpect(jsonPath("$.data.myAccuracy").value(0.0))
                .andExpect(jsonPath("$.data.entries[0].rank").value(1))
                .andExpect(jsonPath("$.data.entries[0].accuracy").value(95.0))
                .andExpect(jsonPath("$.data.entries[?(@.isMe == true)]").isEmpty())
                .andDo(document("ranking/today"));

        assertSnippetCreated("ranking/today");
    }

    @Test
    @DisplayName("GET /api/ranking/today (오늘 피드백 있음) → 본인 항목 포함")
    void todayWithOwnFeedbackIncludesSelf() throws Exception {
        User user = newUser("rkwithfb");
        Script script = seedScript("rkfb");
        completeFeedbackAt(user, script, "Coffee Shop", 84.0, Instant.now());
        String token = issueToken(user);

        mockMvc.perform(get("/api/ranking/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.myRank").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.myAccuracy").value(84.0))
                .andExpect(jsonPath("$.data.entries[?(@.isMe == true)]").exists())
                .andExpect(jsonPath("$.data.unitTitle").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/ranking/today 인증 누락 → 401 UNAUTHORIZED")
    void missingAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/ranking/today"))
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

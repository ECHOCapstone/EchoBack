package com.capstoneecho.echo_back.pronunciation.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

class FeedbackQueryControllerTest extends AbstractControllerIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SentenceSplitter sentenceSplitter;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("GET /api/feedbacks → 200 + 본인 피드백 목록 (createdAt DESC) + REST Docs 스니펫")
    void listReturnsUserScopedFeedback() throws Exception {
        User user = newUser("fblist");
        Script script = seedScript("ListedScript");

        Long fbId1 = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "First", 70.0, "TH", "think", "g")).getId());
        sleepBriefly();
        Long fbId2 = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "Second", 90.0, "AE", "cat", "g")).getId());

        User other = newUser("fblistother");
        transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                other, script, "Hidden", 60.0, "TH", "the", "g")));

        String token = issueToken(user);

        mockMvc.perform(get("/api/feedbacks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(fbId2))
                .andExpect(jsonPath("$.data[0].title").value("Second"))
                .andExpect(jsonPath("$.data[1].id").value(fbId1))
                .andDo(document("feedbacks/list"));

        assertSnippetCreated("feedbacks/list");
    }

    @Test
    @DisplayName("GET /api/feedbacks/{id} → 200 + FeedbackDetailResponse + REST Docs 스니펫")
    void getReturnsDetail() throws Exception {
        User user = newUser("fbget");
        Script script = seedScript("DetailScript");
        Long fbId = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "DetailScript", 80.0, "TH", "think", "g")).getId());
        String token = issueToken(user);

        mockMvc.perform(get("/api/feedbacks/" + fbId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fbId))
                .andExpect(jsonPath("$.data.title").value("DetailScript"))
                .andExpect(jsonPath("$.data.scriptId").value(script.getId()))
                .andDo(document("feedbacks/get"));

        assertSnippetCreated("feedbacks/get");
    }

    @Test
    @DisplayName("GET /api/feedbacks/{id} 존재하지 않음 → 404 FEEDBACK_NOT_FOUND")
    void getMissingReturns404() throws Exception {
        User user = newUser("fbgetmissing");
        String token = issueToken(user);

        mockMvc.perform(get("/api/feedbacks/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FEEDBACK_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/feedbacks/{id} 타 사용자 피드백 → 404 FEEDBACK_NOT_FOUND (cross-tenant)")
    void getCrossTenantReturns404() throws Exception {
        User owner = newUser("fbgetowner");
        User other = newUser("fbgetother");
        Script script = seedScript("CrossTenant");
        Long fbId = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                owner, script, "T", 70.0, null, "the", "g")).getId());
        String token = issueToken(other);

        mockMvc.perform(get("/api/feedbacks/" + fbId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FEEDBACK_NOT_FOUND"));
    }

    @Test
    @DisplayName("#6 세션 하드 삭제 후에도 Feedback 세션 스냅샷(title) 은 보존된다")
    void sessionDeletedFeedbackStillHasSnapshot() throws Exception {
        User user = newUser("fbsnapshot");
        Long userId = user.getId();
        Long sessionId = transactionTemplate.execute(s -> {
            User u = userRepository.findById(userId).orElseThrow();
            Session session = Session.create(u, "MyTalk");
            session.updateScript("Hello world.", sentenceSplitter);
            return sessionRepository.saveAndFlush(session).getId();
        });

        Long fbId = transactionTemplate.execute(s -> {
            Session session = sessionRepository.findById(sessionId).orElseThrow();
            return feedbackRepository.save(
                    PronunciationFeedback.forSession(
                            session.getUser(), session, "MyTalk", 78.0, "AE", "cat",
                            "꾸준한 연습이 발음 개선에 도움이 됩니다.")).getId();
        });

        transactionTemplate.executeWithoutResult(s -> {
            sessionRepository.deleteById(sessionId);
            sessionRepository.flush();
        });

        assertThat(sessionRepository.findById(sessionId)).isEmpty();

        String token = issueToken(user);

        mockMvc.perform(get("/api/feedbacks/" + fbId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fbId))
                .andExpect(jsonPath("$.data.title").value("MyTalk"))
                .andExpect(jsonPath("$.data.sessionId").doesNotExist());
    }

    // ---------- helpers ----------

    private Script seedScript(String title) {
        return transactionTemplate.execute(s -> {
            Track track = trackRepository.save(Track.create("T-" + title + "-" + System.nanoTime(), "d", 1));
            return scriptRepository.save(
                    Script.createChapter(
                            track, 1, title, "content", Difficulty.EASY, null, null));
        });
    }

    private User newUser(String localPart) {
        return transactionTemplate.execute(s -> userRepository.save(User.signup(
                localPart + System.nanoTime(),
                localPart + "-" + System.nanoTime() + "@test.com",
                passwordEncoder.encode("Password1!"), "Nick")));
    }

    private String issueToken(User user) {
        return jwtProvider.issue(
                user.getId(),
                Map.of("username", user.getUsername(), "email", user.getEmail()));
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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

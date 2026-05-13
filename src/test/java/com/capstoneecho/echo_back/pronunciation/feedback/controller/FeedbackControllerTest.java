package com.capstoneecho.echo_back.pronunciation.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import com.capstoneecho.echo_back.support.AnalyzeMockResponses;
import com.capstoneecho.echo_back.support.WavFixtures;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

class FeedbackControllerTest extends AbstractControllerIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private LearningStepRepository stepRepository;
    @Autowired private RecordingRepository recordingRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private ModelServerClient modelServerClient;
    @MockitoBean private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient);
        when(modelServerClient.g2p(anyString())).thenReturn(AnalyzeMockResponses.helloG2p());
        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(AnalyzeMockResponses.perfect());
        when(llmClient.summarizeFeedback(any(LlmContext.class)))
                .thenReturn("꾸준한 연습이 발음 개선에 도움이 됩니다.");
        when(llmClient.retryGuidance(any(LlmContext.class)))
                .thenReturn("천천히 한 번 더 따라 읽어 보세요.");
        when(llmClient.suggestPracticeWord(any(LlmContext.class))).thenReturn("hello");
    }

    @Test
    @DisplayName("POST /api/feedback/generate → 200 + FeedbackDetailResponse + REST Docs 스니펫")
    void generateReturnsDetail() throws Exception {
        ScriptFixture f = seedScriptFlow("fbgen", "GenChapter", "hello");
        Recording r = transactionTemplate.execute(s ->
                recordingRepository.save(
                        Recording.forScriptStep(f.user(), f.script(), f.step(), "u/r.wav", "hello")));
        String token = issueToken(f.user());

        String body = "{\"scriptId\":" + f.script().getId()
                + ",\"recordingIds\":[" + r.getId() + "]}";

        mockMvc.perform(post("/api/feedback/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.scriptId").value(f.script().getId()))
                .andExpect(jsonPath("$.data.sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("GenChapter"))
                .andExpect(jsonPath("$.data.accuracy").isNumber())
                .andExpect(jsonPath("$.data.guidanceKr").isNotEmpty())
                .andExpect(jsonPath("$.data.practiceWord").isNotEmpty())
                .andExpect(jsonPath("$.data.completed").value(false))
                .andDo(document("feedback/generate"));

        assertSnippetCreated("feedback/generate");
    }

    @Test
    @DisplayName("POST /api/feedback/generate scriptId/sessionId 둘 다 없음 → 400 INVALID_REQUEST")
    void generateMissingParentReturns400() throws Exception {
        User user = newUser("fbgenmissing");
        String token = issueToken(user);

        String body = "{\"recordingIds\":[1]}";

        mockMvc.perform(post("/api/feedback/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/feedback/{id}/retry-word multipart → 200 + 갱신된 guidance + REST Docs 스니펫")
    void retryWordReturnsDetail() throws Exception {
        User user = newUser("fbretry");
        Script script = seedScript("RetryScript");
        PronunciationFeedback fb = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "RetryScript", 75.0, "TH", "think", "old")));
        when(llmClient.retryGuidance(any(LlmContext.class)))
                .thenReturn("새 가이드: 천천히 따라 읽어 보세요.");

        String token = issueToken(user);

        mockMvc.perform(multipart("/api/feedback/" + fb.getId() + "/retry-word")
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(fb.getId()))
                .andExpect(jsonPath("$.data.guidanceKr").value("새 가이드: 천천히 따라 읽어 보세요."))
                .andDo(document("feedback/retry-word"));

        assertSnippetCreated("feedback/retry-word");
    }

    @Test
    @DisplayName("POST /api/feedback/{id}/complete → 200 멱등 (2회 호출도 200) + REST Docs 스니펫")
    void completeTwiceReturns200Idempotent() throws Exception {
        User user = newUser("fbcomplete");
        Script script = seedScript("CompleteScript");
        PronunciationFeedback fb = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "CompleteScript", 85.0, "TH", "think", "g")));
        String token = issueToken(user);

        mockMvc.perform(post("/api/feedback/" + fb.getId() + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andDo(document("feedback/complete"));

        mockMvc.perform(post("/api/feedback/" + fb.getId() + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        assertSnippetCreated("feedback/complete");
    }

    @Test
    @DisplayName("POST /api/feedback/{id}/complete 토큰 없음 → 401 UNAUTHORIZED")
    void completeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/feedback/1/complete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ---------- helpers ----------

    private record ScriptFixture(User user, Script script, LearningStep step) {}

    private ScriptFixture seedScriptFlow(String localPart, String chapterTitle, String targetText) {
        User user = newUser(localPart);
        return transactionTemplate.execute(s -> {
            Track track = trackRepository.save(Track.create("T-" + localPart, "d", 1));
            Script script = scriptRepository.save(
                    Script.createChapter(
                            track, 1, chapterTitle, "content", Difficulty.EASY, null, null));
            LearningStep step = stepRepository.save(LearningStep.record(script, "say", targetText));
            return new ScriptFixture(user, script, step);
        });
    }

    private Script seedScript(String title) {
        return transactionTemplate.execute(s -> {
            Track track = trackRepository.save(Track.create("T-s-" + title, "d", 1));
            return scriptRepository.save(
                    Script.createChapter(
                            track, 1, title, "content", Difficulty.EASY, null, null));
        });
    }

    private User newUser(String localPart) {
        return userRepository.save(User.signup(
                localPart + System.nanoTime(),
                localPart + "-" + System.nanoTime() + "@test.com",
                passwordEncoder.encode("Password1!"), "Nick"));
    }

    private String issueToken(User user) {
        return jwtProvider.issue(
                user.getId(),
                Map.of("username", user.getUsername(), "email", user.getEmail()));
    }

    private static MockMultipartFile audioPart(byte[] bytes) {
        return new MockMultipartFile("audio", "audio.wav", "audio/wav", bytes);
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

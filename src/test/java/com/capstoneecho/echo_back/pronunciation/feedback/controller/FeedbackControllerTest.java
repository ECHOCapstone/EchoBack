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
import com.capstoneecho.echo_back.external.llm.LlmComprehensiveContext;
import com.capstoneecho.echo_back.external.llm.LlmRetryContext;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.support.LlmMockResponses;
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
import com.capstoneecho.echo_back.support.TranscribeMockResponses;
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
    @MockitoBean private com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator canonicalGenerator;

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient, canonicalGenerator);
        when(canonicalGenerator.generate(anyString()))
                .thenReturn(new com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult(
                        java.util.List.of(new com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord(
                                "hello", java.util.List.of("HH", "AH", "L", "OW")))));
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(TranscribeMockResponses.perfectTranscribe());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(LlmMockResponses.defaultStep());
        when(llmClient.retryFeedback(any(LlmRetryContext.class)))
                .thenReturn(LlmMockResponses.defaultRetry());
        when(llmClient.comprehensiveFeedback(any(LlmComprehensiveContext.class)))
                .thenReturn(LlmMockResponses.defaultComprehensive());
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
    @DisplayName("POST /api/feedback/{id}/retry-word multipart correct=true → 200 + RetryWordResult 5 필드 + REST Docs 스니펫")
    void retryWordReturnsRetryWordResultWhenCorrect() throws Exception {
        User user = newUser("fbretry");
        Script script = seedScript("RetryScript");
        PronunciationFeedback fb = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "RetryScript", 75.0, "TH", "think", "old")));
        when(llmClient.retryFeedback(any(LlmRetryContext.class)))
                .thenReturn(LlmMockResponses.defaultRetry());

        String token = issueToken(user);

        mockMvc.perform(multipart("/api/feedback/" + fb.getId() + "/retry-word")
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.perceived").isArray())
                .andExpect(jsonPath("$.data.perceived[0]").value("HH"))
                .andExpect(jsonPath("$.data.canonical").isArray())
                .andExpect(jsonPath("$.data.canonical[0]").value("HH"))
                .andExpect(jsonPath("$.data.score").value(85.0))
                .andExpect(jsonPath("$.data.guidanceKr").isNotEmpty())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.accuracy").doesNotExist())
                .andDo(document("feedback/retry-word"));

        assertSnippetCreated("feedback/retry-word");
    }

    @Test
    @DisplayName("POST /api/feedback/{id}/retry-word perceived≠canonical → correct=false, score<100")
    void retryWordReturnsRetryWordResultWhenIncorrect() throws Exception {
        User user = newUser("fbretrywrong");
        Script script = seedScript("RetryScriptWrong");
        PronunciationFeedback fb = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "RetryScriptWrong", 60.0, "AH", "water", "old")));
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(TranscribeMockResponses.misalignedTranscribe());
        when(llmClient.retryFeedback(any(LlmRetryContext.class)))
                .thenReturn(new com.capstoneecho.echo_back.external.llm.LlmRetryFeedback(
                        java.util.List.of(), java.util.List.of(),
                        false, true, "둥글게 발음해 보세요.",
                        com.capstoneecho.echo_back.external.llm.PronunciationGuide.empty(),
                        java.util.List.of()));

        String token = issueToken(user);

        mockMvc.perform(multipart("/api/feedback/" + fb.getId() + "/retry-word")
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.score").value(75.0))
                .andExpect(jsonPath("$.data.guidanceKr").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/feedback/{id}/complete → 200 갱신된 User 응답 + REST Docs 스니펫")
    void completeReturnsUpdatedUserResponse() throws Exception {
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(user.getUsername()))
                .andExpect(jsonPath("$.data.streak").value(1))
                .andExpect(jsonPath("$.data.exp").value(10))
                .andExpect(jsonPath("$.data.completed").doesNotExist())
                .andDo(document("feedback/complete"));

        mockMvc.perform(post("/api/feedback/" + fb.getId() + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(user.getUsername()))
                .andExpect(jsonPath("$.data.exp").value(10));

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

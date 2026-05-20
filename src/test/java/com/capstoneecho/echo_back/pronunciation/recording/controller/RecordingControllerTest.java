package com.capstoneecho.echo_back.pronunciation.recording.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import com.capstoneecho.echo_back.support.AnalyzeMockResponses;
import com.capstoneecho.echo_back.support.LlmMockResponses;
import com.capstoneecho.echo_back.support.WavFixtures;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RecordingControllerTest extends AbstractControllerIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private LearningStepRepository stepRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SentenceSplitter sentenceSplitter;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    @MockitoBean private ModelServerClient modelServerClient;
    @MockitoBean private LlmClient llmClient;
    @MockitoBean private RecordingStorage recordingStorage;

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient, recordingStorage);
        when(modelServerClient.g2p(anyString())).thenReturn(AnalyzeMockResponses.helloG2p());
        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(AnalyzeMockResponses.perfect());
        when(llmClient.summarizeRecording(any(LlmContext.class)))
                .thenReturn(LlmMockResponses.defaultGuidance());
        when(recordingStorage.save(anyLong(), any(byte[].class))).thenReturn("u/recordings/test.wav");
    }

    @Test
    @DisplayName("POST /api/recordings (script-flow) → 201 + RecordingUploadResponse + REST Docs 스니펫")
    void scriptFlowReturns201() throws Exception {
        ScriptFlowFixture f = seedScriptFlow("recuser1", "hello world");
        String token = issueToken(f.user());

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("scriptId", String.valueOf(f.scriptId()))
                        .param("stepId", String.valueOf(f.stepId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.scriptId").value(f.scriptId()))
                .andExpect(jsonPath("$.data.stepId").value(f.stepId()))
                .andExpect(jsonPath("$.data.sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.guidanceKr").isNotEmpty())
                .andExpect(jsonPath("$.data.stepScore").value(100.0))
                .andExpect(jsonPath("$.data.wrongWords").isArray())
                .andExpect(jsonPath("$.data.wrongWords").isEmpty())
                .andDo(document("recordings/upload"));

        assertSnippetCreated("recordings/upload");
    }

    @Test
    @DisplayName("POST /api/recordings (script-flow with-error) → wrongWords[0].word/index 채움")
    void scriptFlowWithErrorReturnsTypedWrongWords() throws Exception {
        ScriptFlowFixture f = seedScriptFlow("recuser1b", "water bottle");
        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(AnalyzeMockResponses.withWaterError());
        when(llmClient.summarizeRecording(any(LlmContext.class)))
                .thenReturn(LlmMockResponses.withWrongWord("water", 0));
        String token = issueToken(f.user());

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("scriptId", String.valueOf(f.scriptId()))
                        .param("stepId", String.valueOf(f.stepId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.wrongWords").isArray())
                .andExpect(jsonPath("$.data.wrongWords[0].word").value("water"))
                .andExpect(jsonPath("$.data.wrongWords[0].index").value(0));
    }

    @Test
    @DisplayName("POST /api/recordings (session-sentence) → 201 + sessionId/sessionSentenceId 채움")
    void sessionSentenceReturns201() throws Exception {
        User user = savedUser("recuser2");
        Session session = Session.create(user, "내 연습");
        session.updateScript("Hello world.", sentenceSplitter);
        Session saved = sessionRepository.saveAndFlush(session);
        Long sentenceId = saved.getSentences().get(0).getId();

        String token = issueToken(user);

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("sessionId", String.valueOf(saved.getId()))
                        .param("sessionSentenceId", String.valueOf(sentenceId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(saved.getId()))
                .andExpect(jsonPath("$.data.sessionSentenceId").value(sentenceId))
                .andExpect(jsonPath("$.data.scriptId").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/recordings sessionId 만 (sessionSentenceId 누락) → 400 INVALID_REQUEST")
    void missingSessionSentenceReturns400InvalidRequest() throws Exception {
        User user = savedUser("recuser3");
        Session saved = sessionRepository.save(Session.create(user, "Free"));

        String token = issueToken(user);

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("sessionId", String.valueOf(saved.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/recordings parent 미지정 → 400 INVALID_REQUEST")
    void missingParentReturns400InvalidRequest() throws Exception {
        User user = savedUser("recuser4");
        String token = issueToken(user);

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/recordings 헤더 깨진 오디오 → 400 AUDIO_DECODE_FAILED")
    void corruptAudioReturns400AudioDecodeFailed() throws Exception {
        ScriptFlowFixture f = seedScriptFlow("recuser5", "hello");
        String token = issueToken(f.user());

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.CORRUPT_AUDIO))
                        .param("scriptId", String.valueOf(f.scriptId()))
                        .param("stepId", String.valueOf(f.stepId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUDIO_DECODE_FAILED"));
    }

    @Test
    @DisplayName("POST /api/recordings 미존재 scriptId → 404 SCRIPT_NOT_FOUND")
    void unknownScriptReturns404() throws Exception {
        User user = savedUser("recuser6");
        Script script = scriptRepository.save(
                Script.createStandalone("S", "c", Difficulty.EASY));
        LearningStep step = stepRepository.save(LearningStep.record(script, "say", "hi"));
        String token = issueToken(user);

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("scriptId", "999999")
                        .param("stepId", String.valueOf(step.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SCRIPT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/recordings 미존재 stepId → 404 STEP_NOT_FOUND")
    void unknownStepReturns404() throws Exception {
        ScriptFlowFixture f = seedScriptFlow("recuser7", "hi");
        String token = issueToken(f.user());

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("scriptId", String.valueOf(f.scriptId()))
                        .param("stepId", "999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STEP_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/recordings 미존재 sessionId → 404 SESSION_NOT_FOUND")
    void unknownSessionReturns404() throws Exception {
        User user = savedUser("recuser8");
        String token = issueToken(user);

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("sessionId", "999999")
                        .param("sessionSentenceId", "999998")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/recordings 미존재 sessionSentenceId → 404 SESSION_SENTENCE_NOT_FOUND")
    void unknownSessionSentenceReturns404() throws Exception {
        User user = savedUser("recuser9");
        Session saved = sessionRepository.save(Session.create(user, "Empty"));
        String token = issueToken(user);

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("sessionId", String.valueOf(saved.getId()))
                        .param("sessionSentenceId", "999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_SENTENCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/recordings 모델 서버 5xx → 502 MODEL_SERVER_ERROR")
    void modelServer5xxReturns502() throws Exception {
        ScriptFlowFixture f = seedScriptFlow("recuser10", "hi");
        when(modelServerClient.g2p(anyString()))
                .thenThrow(new BusinessException(ErrorCode.MODEL_SERVER_ERROR, "upstream 5xx"));
        String token = issueToken(f.user());

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("scriptId", String.valueOf(f.scriptId()))
                        .param("stepId", String.valueOf(f.stepId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("MODEL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("POST /api/recordings 모델 서버 응답 없음 → 503 MODEL_SERVER_UNAVAILABLE")
    void modelServerUnavailableReturns503() throws Exception {
        ScriptFlowFixture f = seedScriptFlow("recuser11", "hi");
        when(modelServerClient.g2p(anyString()))
                .thenThrow(new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, "timeout"));
        String token = issueToken(f.user());

        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("scriptId", String.valueOf(f.scriptId()))
                        .param("stepId", String.valueOf(f.stepId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("MODEL_SERVER_UNAVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/recordings 토큰 없음 → 401 UNAUTHORIZED")
    void uploadWithoutTokenReturns401() throws Exception {
        mockMvc.perform(multipartUpload()
                        .file(audioPart(WavFixtures.VALID_WAV))
                        .param("sessionId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ---------- helpers ----------

    private record ScriptFlowFixture(User user, Long scriptId, Long stepId) {}

    private ScriptFlowFixture seedScriptFlow(String localPart, String targetText) {
        User user = savedUser(localPart);
        Track track = trackRepository.save(Track.create("T", "d", 1));
        Script script = scriptRepository.save(
                Script.createChapter(track, 1, "Ch", "content", Difficulty.EASY, null, null));
        LearningStep step = stepRepository.save(LearningStep.record(script, "say", targetText));
        return new ScriptFlowFixture(user, script.getId(), step.getId());
    }

    private User savedUser(String localPart) {
        return userRepository.save(User.signup(
                localPart, localPart + "@test.com", passwordEncoder.encode("Password1!"), "Nick"));
    }

    private String issueToken(User user) {
        return jwtProvider.issue(
                user.getId(),
                Map.of("username", user.getUsername(), "email", user.getEmail()));
    }

    private static MockMultipartHttpServletRequestBuilder multipartUpload() {
        return multipart("/api/recordings");
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

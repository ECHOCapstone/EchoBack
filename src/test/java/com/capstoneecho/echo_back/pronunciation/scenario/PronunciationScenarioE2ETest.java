package com.capstoneecho.echo_back.pronunciation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.PronunciationGuide;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalCacheService;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadRequest;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
import com.capstoneecho.echo_back.pronunciation.recording.service.RecordingService;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 백엔드 책임 분기를 명시적으로 단언한다. 모델 서버 응답 / LLM 점수 / canonical 캐시를 mock 으로 주입.
@SpringBootTest
@ActiveProfiles("test")
class PronunciationScenarioE2ETest {

    private static final byte[] VALID_WAV =
            new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

    @Autowired private RecordingService recordingService;
    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private LearningStepRepository stepRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SentenceSplitter sentenceSplitter;

    @MockitoBean private ModelServerClient modelServerClient;
    @MockitoBean private LlmClient llmClient;
    @MockitoBean private CanonicalCacheService canonicalCacheService;
    @MockitoBean private RecordingStorage recordingStorage;

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient, canonicalCacheService, recordingStorage);
        when(canonicalCacheService.resolveForStep(anyLong()))
                .thenReturn(appleCanonical());
        when(canonicalCacheService.resolveForSentence(anyLong()))
                .thenReturn(appleCanonical());
        when(recordingStorage.save(anyLong(), any(byte[].class)))
                .thenReturn("u/202605/test.wav");
    }

    @Test
    @DisplayName("TC-01: LLM 점수 ≥ 임계 + 권고 없음 → 응답 passed=true / retryRecommended=false")
    void tc01_normalPronunciationIsMarkedPassed() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(perfectTranscribe());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(92, false, List.of()));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        assertThat(r.stepScore()).isGreaterThanOrEqualTo(90.0);
        assertThat(r.passed()).isTrue();
        assertThat(r.retryRecommended()).isFalse();
    }

    @Test
    @DisplayName("TC-02: 오발음 errors + LLM 재학습 권고 → 응답 retryRecommended=true / passed=false")
    void tc02_misPronunciationTriggersRetryRecommendation() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(perfectTranscribe());
        LlmPhonemeError err = new LlmPhonemeError(AlignmentOp.ErrorType.SUBSTITUTION, "AE", "AH", 0);
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(65, true, List.of(err)));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        assertThat(r.retryRecommended()).isTrue();
        assertThat(r.passed()).isFalse();
        assertThat(r.errors()).isNotEmpty();
    }

    @Test
    @DisplayName("TC-05: 무음 입력 (perceived 빈 리스트) → LLM 0점 + retryRecommended=true")
    void tc05_silentInputYieldsZeroScoreAndRetry() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(silentTranscribe());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(0, true, List.of()));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        assertThat(r.stepScore()).isEqualTo(0.0);
        assertThat(r.passed()).isFalse();
        assertThat(r.retryRecommended()).isTrue();
        assertThat(r.perceived()).isEmpty();
    }

    @Test
    @DisplayName("TC-08: 같은 step 두 번 → 두 번째 LLM 호출의 priorAttempts 에 첫 시도가 포함된다")
    void tc08_repeatedAttemptsAreFedToLlmAsPriorContext() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(perfectTranscribe());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(70, true, List.of()));

        recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);
        recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        ArgumentCaptor<LlmStepContext> captor = ArgumentCaptor.forClass(LlmStepContext.class);
        verify(llmClient, times(2)).stepFeedback(captor.capture());
        List<LlmStepContext> contexts = captor.getAllValues();
        assertThat(contexts.get(0).priorAttempts())
                .as("첫 호출은 priorAttempts 가 비어 있다")
                .isEmpty();
        assertThat(contexts.get(1).priorAttempts())
                .as("두 번째 호출은 첫 시도 1건이 priorAttempts 로 들어와야 한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("TC-09: 추천 학습 (script-flow) 점수 ≥ 75 → passed=true")
    void tc09_scriptFlowPassesAtOrAbove75() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(perfectTranscribe());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(80, false, List.of()));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        assertThat(r.stepScore()).isEqualTo(80.0);
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("TC-10: 맞춤 학습 (session-sentence) 점수 ≥ 75 → passed=true")
    void tc10_sessionFlowPassesAtOrAbove75() {
        SessionFlowFixture f = seedSessionFlow("apple is red.");
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(perfectTranscribe());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(85, false, List.of()));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(),
                new RecordingUploadRequest(null, null, f.sessionId(), f.sentenceId()),
                VALID_WAV);

        assertThat(r.sessionId()).isEqualTo(f.sessionId());
        assertThat(r.sessionSentenceId()).isEqualTo(f.sentenceId());
        assertThat(r.stepScore()).isEqualTo(85.0);
        assertThat(r.passed()).isTrue();
    }

    // ---------- helpers ----------

    private record ScriptFlowFixture(Long userId, Long scriptId, Long stepId) {}

    private record SessionFlowFixture(Long userId, Long sessionId, Long sentenceId) {}

    private ScriptFlowFixture seedScriptFlow(String targetText) {
        User user = userRepository.save(
                User.fromOAuth2("scenario" + System.nanoTime() + "@example.com", "Scenario"));
        Track track = trackRepository.save(Track.create("ScenarioTrack", "d", 1));
        Script script = scriptRepository.save(
                Script.createChapter(track, 1, "Ch", "content", Difficulty.EASY, null, null));
        LearningStep step = stepRepository.save(LearningStep.record(script, "say", targetText));
        return new ScriptFlowFixture(user.getId(), script.getId(), step.getId());
    }

    private SessionFlowFixture seedSessionFlow(String scriptText) {
        User user = userRepository.save(
                User.fromOAuth2("scn-s" + System.nanoTime() + "@example.com", "ScnSess"));
        Session session = Session.create(user, "ScenarioSession");
        session.updateScript(scriptText, sentenceSplitter);
        Session saved = sessionRepository.save(session);
        SessionSentence sentence = saved.getSentences().get(0);
        return new SessionFlowFixture(user.getId(), saved.getId(), sentence.getId());
    }

    private static LlmStepFeedback stepLlm(int score, boolean retry, List<LlmPhonemeError> errors) {
        return new LlmStepFeedback(
                score, List.of(), errors, retry, "ok",
                PronunciationGuide.empty(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static CanonicalResult appleCanonical() {
        return new CanonicalResult(List.of(
                new CanonicalWord("apple", List.of("AE", "P", "AH", "L"))));
    }

    private static TranscribeResult perfectTranscribe() {
        return new TranscribeResult(
                List.of("AE", "P", "AH", "L"),
                List.of(0.95, 0.92, 0.88, 0.9),
                1.0,
                SpeechRate.NORMAL,
                1.0,
                "echo-baseline",
                "echo");
    }

    private static TranscribeResult silentTranscribe() {
        return new TranscribeResult(
                List.of(),
                List.of(),
                0.5,
                SpeechRate.NORMAL,
                1.0,
                "echo-baseline",
                "echo");
    }
}

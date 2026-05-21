package com.capstoneecho.echo_back.pronunciation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
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

// docs/diagrams/19~21-test-scenarios SVG 의 TC-01 ~ TC-10 중 백엔드 책임 분기를 명시적으로 단언한다.
// TC-03/04/06/12/13 는 모델 서버, TC-14/15/18 은 프론트 책임이라 본 슬라이스 범위 밖.
// TC-11/16/17 은 별도 도메인 테스트가 이미 커버한다 (TrackControllerTest / FeedbackServiceConcurrencyTest / StatsServiceTest).
@SpringBootTest
@ActiveProfiles("test")
class PronunciationScenarioE2ETest {

    // 백엔드 통과 임계 (app.gamification.pass-threshold=80) 을 기준으로 점수를 양 / 음 양쪽에 두고 단언한다.
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
    @MockitoBean private RecordingStorage recordingStorage;

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient, recordingStorage);
        when(modelServerClient.g2p(anyString()))
                .thenReturn(new G2pResult("AE P AH L", List.of()));
        when(recordingStorage.save(anyLong(), any(byte[].class)))
                .thenReturn("u/202605/test.wav");
    }

    @Test
    @DisplayName("TC-01: 정확도 90% 이상 + LLM 권고 없음 → 응답 passed=true / retryRecommended=false")
    void tc01_normalPronunciationIsMarkedPassed() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.analyze(any(byte[].class), anyString())).thenReturn(perfect());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(92, false));

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
        when(modelServerClient.analyze(any(byte[].class), anyString())).thenReturn(withError());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(65, true));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        assertThat(r.retryRecommended()).isTrue();
        assertThat(r.passed()).isFalse();
        assertThat(r.errors()).isNotEmpty();
    }

    @Test
    @DisplayName("TC-03: 묵음 처리 — G2P 가 묵음 음소를 뺀 canonical 을 돌려주면 그 값이 LLM 컨텍스트로 전달된다")
    void tc03_silentLettersAreExcludedFromCanonical() {
        // "knight" 의 K 가 묵음이라 G2P 응답이 N AY T 만 돌려준다고 가정한다.
        // 백엔드는 모델 서버 G2P 결과를 그대로 LLM 컨텍스트에 실어야 한다 (자체 추측 / 보강 없이).
        ScriptFlowFixture f = seedScriptFlow("knight");
        when(modelServerClient.g2p(anyString()))
                .thenReturn(new G2pResult("N AY T", List.of()));
        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(new AnalyzeResult(
                        List.of("N", "AY", "T"),
                        List.of("N", "AY", "T"),
                        List.of(0.92, 0.9, 0.88),
                        List.of(),
                        List.of(),
                        0.0,
                        0.6,
                        SpeechRate.NORMAL));
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(95, false));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        // (1) 모델 서버에 보낸 canonical 인자에 K 가 없어야 한다.
        ArgumentCaptor<String> g2pToAnalyze = ArgumentCaptor.forClass(String.class);
        verify(modelServerClient).analyze(any(byte[].class), g2pToAnalyze.capture());
        assertThat(g2pToAnalyze.getValue()).isEqualTo("N AY T").doesNotContain("K");

        // (2) LLM 컨텍스트의 canonical / perceived 모두에 K 가 없어야 한다.
        ArgumentCaptor<LlmStepContext> llmCtx = ArgumentCaptor.forClass(LlmStepContext.class);
        verify(llmClient).stepFeedback(llmCtx.capture());
        assertThat(llmCtx.getValue().canonical()).containsExactly("N", "AY", "T");
        assertThat(llmCtx.getValue().perceived()).containsExactly("N", "AY", "T");
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("TC-05: 무음 입력 (perceived 빈 리스트 + PER=1) → 점수 0 + retryRecommended=true")
    void tc05_silentInputYieldsZeroScoreAndRetry() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.analyze(any(byte[].class), anyString())).thenReturn(silent());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(0, true));

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
        when(modelServerClient.analyze(any(byte[].class), anyString())).thenReturn(withError());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(70, true));

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
    @DisplayName("TC-09: 추천 학습 (script-flow) 점수 ≥ 80 → passed=true")
    void tc09_scriptFlowPassesAtOrAbove80() {
        ScriptFlowFixture f = seedScriptFlow("apple");
        when(modelServerClient.analyze(any(byte[].class), anyString())).thenReturn(scoring(80.0));
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(80, false));

        RecordingUploadResponse r = recordingService.upload(
                f.userId(), new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null), VALID_WAV);

        assertThat(r.stepScore()).isEqualTo(80.0);
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("TC-10: 맞춤 학습 (session-sentence) 점수 ≥ 80 → passed=true")
    void tc10_sessionFlowPassesAtOrAbove80() {
        SessionFlowFixture f = seedSessionFlow("apple is red.");
        when(modelServerClient.analyze(any(byte[].class), anyString())).thenReturn(scoring(85.0));
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(stepLlm(85, false));

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

    private static LlmStepFeedback stepLlm(int score, boolean retry) {
        return new LlmStepFeedback(
                score, retry, "ok",
                List.of(), List.of(), List.of(), List.of());
    }

    private static AnalyzeResult perfect() {
        return new AnalyzeResult(
                List.of("AE", "P", "AH", "L"),
                List.of("AE", "P", "AH", "L"),
                List.of(0.95, 0.92, 0.88, 0.9),
                List.of(),
                List.of(),
                0.05,
                1.0,
                SpeechRate.NORMAL);
    }

    private static AnalyzeResult withError() {
        AnalyzeError err = new AnalyzeError("SUB", 0, "AH", "AE");
        return new AnalyzeResult(
                List.of("AH", "P", "AH", "L"),
                List.of("AE", "P", "AH", "L"),
                List.of(0.85, 0.9, 0.8, 0.85),
                List.of(),
                List.of(err),
                0.35,
                1.0,
                SpeechRate.NORMAL);
    }

    private static AnalyzeResult silent() {
        return new AnalyzeResult(
                List.of(),
                List.of("AE", "P", "AH", "L"),
                List.of(),
                List.of(),
                List.of(),
                1.0,
                0.5,
                SpeechRate.NORMAL);
    }

    // 백엔드 ScoringPolicy.perToScore: (1 - per) * 100. 원하는 점수에서 역산해 PER 을 만든다.
    private static AnalyzeResult scoring(double score) {
        double per = 1.0 - (score / 100.0);
        return new AnalyzeResult(
                List.of("AE", "P", "AH", "L"),
                List.of("AE", "P", "AH", "L"),
                List.of(0.9, 0.9, 0.9, 0.9),
                List.of(),
                List.of(),
                per,
                1.0,
                SpeechRate.NORMAL);
    }
}

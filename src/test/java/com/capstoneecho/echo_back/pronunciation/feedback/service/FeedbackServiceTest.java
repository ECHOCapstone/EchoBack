package com.capstoneecho.echo_back.pronunciation.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmComprehensiveContext;
import com.capstoneecho.echo_back.external.llm.LlmRetryContext;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.DefaultSentenceSplitter;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackDetailResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackGenerateRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.RetryWordResult;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.support.LlmMockResponses;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class FeedbackServiceTest {

    private static final byte[] VALID_WAV =
            com.capstoneecho.echo_back.support.WavFixtures.VALID_WAV;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private LearningStepRepository stepRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private ModelServerClient modelServerClient;

    @MockitoBean
    private LlmClient llmClient;

    @MockitoBean
    private com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator canonicalGenerator;

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient, canonicalGenerator);
        when(canonicalGenerator.generate(anyString()))
                .thenReturn(new com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult(
                        List.of(new com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord(
                                "hello", List.of("HH", "AH", "L", "OW")))));
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(perfectTranscribe());
        when(llmClient.stepFeedback(any(LlmStepContext.class)))
                .thenReturn(LlmMockResponses.defaultStep());
        when(llmClient.retryFeedback(any(LlmRetryContext.class)))
                .thenReturn(LlmMockResponses.defaultRetry());
        when(llmClient.comprehensiveFeedback(any(LlmComprehensiveContext.class)))
                .thenReturn(LlmMockResponses.defaultComprehensive());
    }

    @Test
    @DisplayName("generate(script-flow) 는 스크립트 컨텍스트의 녹음을 누적해 피드백을 생성한다")
    void generateScriptFlowAggregatesRecordings() {
        ScriptFixture f = seedScriptFlow("hello", "Coffee Shop");
        Recording r1 = transactionTemplate.execute(status ->
                recordingRepository.save(
                        Recording.forScriptStep(f.user, f.script, f.step, "u/r1.wav", "hello")));

        FeedbackDetailResponse response = feedbackService.generate(
                f.user.getId(),
                new FeedbackGenerateRequest(f.script.getId(), null, List.of(r1.getId())));

        assertThat(response.id()).isNotNull();
        assertThat(response.scriptId()).isEqualTo(f.script.getId());
        assertThat(response.sessionId()).isNull();
        assertThat(response.title()).isEqualTo("Coffee Shop");
        assertThat(response.accuracy()).isBetween(0.0, 100.0);
        assertThat(response.guidanceKr()).isNotBlank();
        assertThat(response.practiceWord()).isNotBlank();
        assertThat(response.completed()).isFalse();
    }

    @Test
    @DisplayName("generate 는 같은 (user, 녹음 집합) 으로 반복 호출해도 피드백을 한 개만 만든다 (멱등 — 경험치 복제 차단)")
    void generateIsIdempotentForSameRecordingSet() {
        ScriptFixture f = seedScriptFlow("hello", "Coffee Shop");
        Recording r1 = transactionTemplate.execute(status ->
                recordingRepository.save(
                        Recording.forScriptStep(f.user, f.script, f.step, "u/r1.wav", "hello")));
        FeedbackGenerateRequest req =
                new FeedbackGenerateRequest(f.script.getId(), null, List.of(r1.getId()));

        FeedbackDetailResponse first = feedbackService.generate(f.user.getId(), req);
        FeedbackDetailResponse second = feedbackService.generate(f.user.getId(), req);

        // 두 번째 호출은 새 row 를 만들지 않고 기존 피드백을 그대로 돌려준다.
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(feedbackRepository.findAllByUser_IdOrderByCreatedAtDesc(f.user.getId()))
                .as("같은 녹음 집합으로는 피드백이 한 개만 존재해야 한다")
                .hasSize(1);
        // 빠른 경로(캐시 반환)라 LLM 종합 피드백은 첫 호출 때 1번만 불린다.
        Mockito.verify(llmClient, Mockito.times(1))
                .comprehensiveFeedback(any(LlmComprehensiveContext.class));
    }

    @Test
    @DisplayName("#8 generate 는 cross-context 녹음 ID 가 섞이면 RECORDING_NOT_FOUND 를 던진다")
    void generateCrossContextRejectsScriptAndSession() {
        ScriptFixture script = seedScriptFlow("hello", "ScriptA");
        ScriptFixture other = seedScriptFlowForUser(script.user, "world", "ScriptB");
        Recording crossRec = transactionTemplate.execute(status ->
                recordingRepository.save(
                        Recording.forScriptStep(
                                other.user, other.script, other.step, "u/x.wav", "world")));

        FeedbackGenerateRequest req = new FeedbackGenerateRequest(
                script.script.getId(), null, List.of(crossRec.getId()));

        assertThatThrownBy(() -> feedbackService.generate(script.user.getId(), req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.RECORDING_NOT_FOUND);

        SessionFixture sf = seedSession(script.user, "MyTalk", "Hello world.");
        Recording sessionRec = transactionTemplate.execute(status ->
                recordingRepository.save(
                        Recording.forSessionSentence(
                                sf.user, sf.session, sf.firstSentence(),
                                "u/s.wav", "Hello world.")));

        FeedbackGenerateRequest mixedReq = new FeedbackGenerateRequest(
                script.script.getId(), null, List.of(sessionRec.getId()));

        assertThatThrownBy(() -> feedbackService.generate(script.user.getId(), mixedReq))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.RECORDING_NOT_FOUND);
    }

    @Test
    @DisplayName("#10 complete 는 타 사용자 feedbackId 에 대해 FEEDBACK_NOT_FOUND 를 던진다")
    void completeOtherUsersFeedbackReturns404() {
        User owner = newUser("owner");
        User other = newUser("other");
        Script script = seedScript("Owned");
        PronunciationFeedback fb = transactionTemplate.execute(status ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                owner, script, "T", 80.0, "TH", "think", "guide")));

        assertThatThrownBy(() -> feedbackService.complete(other.getId(), fb.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND);

        User reloaded = userRepository.findById(other.getId()).orElseThrow();
        assertThat(reloaded.getExp()).isZero();
        assertThat(reloaded.getStreak()).isZero();
        PronunciationFeedback reloadedFb = feedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(reloadedFb.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("complete (정상) 는 첫 호출에 EXP 를 가산하고 두 번째 호출은 idempotent 다")
    void completeAwardsExpOnceAndIdempotent() {
        User user = newUser("u-complete");
        Script script = seedScript("S");
        PronunciationFeedback fb = transactionTemplate.execute(status ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "T", 85.0, "TH", "think", "guide")));

        UserResponse first = feedbackService.complete(user.getId(), fb.getId());
        UserResponse second = feedbackService.complete(user.getId(), fb.getId());

        assertThat(first.exp()).isEqualTo(10);
        assertThat(first.streak()).isEqualTo(1);
        assertThat(first.username()).isEqualTo(user.getUsername());
        assertThat(second.exp()).isEqualTo(10);
        assertThat(second.streak()).isEqualTo(1);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getExp()).isEqualTo(10);
        assertThat(reloaded.getStreak()).isEqualTo(1);
        PronunciationFeedback reloadedFb = feedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(reloadedFb.isCompleted()).isTrue();
        assertThat(reloadedFb.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("retryWord 는 audio + perceived/canonical 일치 시 RetryWordResult(correct=true) 반환, feedback 은 변경되지 않는다")
    void retryWordReturnsRetryWordResultAndDoesNotPersist() {
        User user = newUser("u-retry");
        Script script = seedScript("RetryScript");
        PronunciationFeedback fb = transactionTemplate.execute(status ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "T", 70.0, "TH", "think", "old guide")));

        when(llmClient.retryFeedback(any(LlmRetryContext.class)))
                .thenReturn(LlmMockResponses.defaultRetry());

        RetryWordResult response =
                feedbackService.retryWord(user.getId(), fb.getId(), VALID_WAV);

        assertThat(response.correct()).isTrue();
        assertThat(response.perceived()).containsExactly("HH", "AH", "L", "OW");
        assertThat(response.canonical()).containsExactly("HH", "AH", "L", "OW");
        assertThat(response.score()).isEqualTo(100.0);
        assertThat(response.guidanceKr()).isNotBlank();

        PronunciationFeedback reloaded = feedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(reloaded.getGuidanceKr())
                .as("retry-word is read-only and must not persist guidance to feedback")
                .isEqualTo("old guide");
    }

    @Test
    @DisplayName("retryWord 는 타 사용자 feedbackId 에 대해 FEEDBACK_NOT_FOUND 를 던진다")
    void retryWordOtherUsersFeedbackReturns404() {
        User owner = newUser("retry-owner");
        User other = newUser("retry-other");
        Script script = seedScript("R2");
        PronunciationFeedback fb = transactionTemplate.execute(status ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                owner, script, "T", 70.0, null, "the", "old")));

        assertThatThrownBy(() ->
                feedbackService.retryWord(other.getId(), fb.getId(), VALID_WAV))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND);
    }

    // ---------- helpers ----------

    private static TranscribeResult perfectTranscribe() {
        return new TranscribeResult(
                List.of("HH", "AH", "L", "OW"),
                List.of(0.9, 0.85, 0.8, 0.75),
                1.23,
                SpeechRate.NORMAL,
                1.0,
                "echo-baseline",
                "echo");
    }

    private record ScriptFixture(User user, Script script, LearningStep step) {}

    private record SessionFixture(User user, Session session) {
        SessionSentence firstSentence() {
            return session.getSentences().get(0);
        }
    }

    private ScriptFixture seedScriptFlow(String targetText, String title) {
        User user = newUser("script");
        return seedScriptFlowForUser(user, targetText, title);
    }

    private ScriptFixture seedScriptFlowForUser(User user, String targetText, String title) {
        return transactionTemplate.execute(status -> {
            Track track = trackRepository.save(Track.create("T-" + title, "d", 1));
            Script script = scriptRepository.save(
                    Script.createChapter(
                            track, 1, title, "content", Difficulty.EASY, null, null));
            LearningStep step = stepRepository.save(
                    LearningStep.record(script, "say", targetText));
            return new ScriptFixture(user, script, step);
        });
    }

    private SessionFixture seedSession(User user, String title, String scriptText) {
        return transactionTemplate.execute(status -> {
            Session session = sessionRepository.save(Session.create(user, title));
            session.updateScript(scriptText, new DefaultSentenceSplitter());
            sessionRepository.flush();
            return new SessionFixture(user, session);
        });
    }

    private Script seedScript(String title) {
        return transactionTemplate.execute(status -> {
            Track track = trackRepository.save(Track.create("T-s-" + title, "d", 1));
            return scriptRepository.save(
                    Script.createChapter(track, 1, title, "content", Difficulty.EASY, null, null));
        });
    }

    private User newUser(String localPart) {
        return userRepository.save(
                User.fromOAuth2(localPart + "-" + System.nanoTime() + "@example.com", localPart));
    }
}

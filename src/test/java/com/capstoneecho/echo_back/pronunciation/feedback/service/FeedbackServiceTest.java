package com.capstoneecho.echo_back.pronunciation.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
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
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackDetailResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackGenerateRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import java.util.List;
import java.util.Optional;
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
            new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

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

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient);
        when(modelServerClient.g2p(anyString()))
                .thenReturn(new G2pResult("HH AH L OW", List.of()));
        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(perfectAnalyzeResult());
        when(llmClient.summarizeFeedback(any(LlmContext.class)))
                .thenReturn("꾸준한 연습이 발음 개선에 도움이 됩니다.");
        when(llmClient.retryGuidance(any(LlmContext.class)))
                .thenReturn("천천히 한 번 더 따라 읽어 보세요.");
        when(llmClient.suggestPracticeWord(any(LlmContext.class)))
                .thenReturn("hello");
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
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RECORDING_NOT_FOUND);

        SessionFixture sf = seedSession(script.user, "MyTalk", "Hello world.");
        Recording sessionRec = transactionTemplate.execute(status ->
                recordingRepository.save(
                        Recording.forSessionFreeForm(
                                sf.user, sf.session, "u/s.wav", "Hello world.")));

        FeedbackGenerateRequest mixedReq = new FeedbackGenerateRequest(
                script.script.getId(), null, List.of(sessionRec.getId()));

        assertThatThrownBy(() -> feedbackService.generate(script.user.getId(), mixedReq))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RECORDING_NOT_FOUND);
    }

    @Test
    @DisplayName("#9 generate 는 session-free-form (sentence NULL) 녹음을 정상적으로 누적한다")
    void orphanRecordingsAreAggregatedSuccessfully() {
        User user = newUser("orphan");
        SessionFixture sf = seedSession(user, "FreeFormTalk", "Hello world.");
        Recording sentenceRec = transactionTemplate.execute(status ->
                recordingRepository.save(
                        Recording.forSessionSentence(
                                sf.user, sf.session, sf.firstSentence(),
                                "u/sentence.wav", "Hello world.")));
        Recording freeFormRec = transactionTemplate.execute(status ->
                recordingRepository.save(
                        Recording.forSessionFreeForm(
                                sf.user, sf.session, "u/free.wav", "Hello world.")));

        FeedbackDetailResponse response = feedbackService.generate(
                sf.user.getId(),
                new FeedbackGenerateRequest(
                        null, sf.session.getId(),
                        List.of(sentenceRec.getId(), freeFormRec.getId())));

        assertThat(response.id()).isNotNull();
        assertThat(response.sessionId()).isEqualTo(sf.session.getId());
        assertThat(response.scriptId()).isNull();
        assertThat(response.title()).isEqualTo("FreeFormTalk");
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
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
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

        FeedbackDetailResponse first = feedbackService.complete(user.getId(), fb.getId());
        FeedbackDetailResponse second = feedbackService.complete(user.getId(), fb.getId());

        assertThat(first.completed()).isTrue();
        assertThat(first.completedAt()).isNotNull();
        assertThat(second.completed()).isTrue();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getExp()).isEqualTo(FeedbackService.DEFAULT_COMPLETION_EXP);
        assertThat(reloaded.getStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("retryWord 는 audio + 피드백 가이드를 갱신해 FeedbackDetailResponse 를 반환한다")
    void retryWordAppendsRecordingAndUpdatesGuidance() {
        User user = newUser("u-retry");
        Script script = seedScript("RetryScript");
        PronunciationFeedback fb = transactionTemplate.execute(status ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "T", 70.0, "TH", "think", "old guide")));

        when(llmClient.retryGuidance(any(LlmContext.class)))
                .thenReturn("새 가이드: 천천히 따라 읽어 보세요.");

        FeedbackDetailResponse response =
                feedbackService.retryWord(user.getId(), fb.getId(), VALID_WAV, null);

        assertThat(response.guidanceKr()).isEqualTo("새 가이드: 천천히 따라 읽어 보세요.");
        PronunciationFeedback reloaded = feedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(reloaded.getGuidanceKr()).isEqualTo("새 가이드: 천천히 따라 읽어 보세요.");
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
                feedbackService.retryWord(other.getId(), fb.getId(), VALID_WAV, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND);
    }

    // ---------- helpers ----------

    private static AnalyzeResult perfectAnalyzeResult() {
        return new AnalyzeResult(
                List.of("HH", "AH", "L", "OW"),
                Optional.of(List.of("HH", "AH", "L", "OW")),
                List.of(0.9, 0.85, 0.8, 0.75),
                List.of(),
                List.of(),
                Optional.of(0.0),
                1.23);
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

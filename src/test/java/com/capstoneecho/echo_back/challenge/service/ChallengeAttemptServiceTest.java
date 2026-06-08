package com.capstoneecho.echo_back.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.challenge.dto.ChallengeAttemptResponse;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.entity.DailyChallengeAttempt;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.PronunciationGuide;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PriorAttemptAssembler;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringService;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

// ChallengeAttemptService.submit 의 cache hit / cache miss / 한도 초과 / 빈 audio 분기를 검증한다.
// Spring 컨텍스트 없이 Mockito 만으로 빌드해 가볍게 돈다.
class ChallengeAttemptServiceTest {

    private static final byte[] VALID_WAV = new byte[200];

    static {
        // WavHeaderValidator 는 mock 으로 우회하므로 내용은 의미 없다 (any byte[] ok).
    }

    private ChallengeService challengeService;
    private DailyChallengeAttemptRepository attemptRepository;
    private UserRepository userRepository;
    private WavHeaderValidator wavHeaderValidator;
    private ModelServerClient modelServerClient;
    private LlmClient llmClient;
    private CanonicalJson canonicalJson;
    private ScoringService scoringService;
    private RecordingStorage recordingStorage;
    private PriorAttemptAssembler priorAttemptAssembler;
    private StatsZoneProvider statsZoneProvider;
    private RuntimeSettings settings;
    private PlatformTransactionManager txManager;
    private ChallengeAttemptService service;

    @BeforeEach
    void setUp() {
        challengeService = mock(ChallengeService.class);
        attemptRepository = mock(DailyChallengeAttemptRepository.class);
        userRepository = mock(UserRepository.class);
        wavHeaderValidator = mock(WavHeaderValidator.class);
        modelServerClient = mock(ModelServerClient.class);
        llmClient = mock(LlmClient.class);
        canonicalJson = new CanonicalJson(new ObjectMapper());
        scoringService = mock(ScoringService.class);
        recordingStorage = mock(RecordingStorage.class);
        priorAttemptAssembler = mock(PriorAttemptAssembler.class);
        statsZoneProvider = mock(StatsZoneProvider.class);
        settings = mock(RuntimeSettings.class);
        txManager = mock(PlatformTransactionManager.class);
        lenient().when(statsZoneProvider.zone()).thenReturn(ZoneId.of("Asia/Seoul"));
        lenient().when(settings.passThreshold()).thenReturn(75.0);

        // writeTx.execute 가 callback 을 즉시 실행하도록 mock TransactionManager 가 SimpleTransactionStatus 를
        // 돌려주게 둔다. ChallengeAttemptService 안의 TransactionTemplate 는 commit/rollback 을 호출해도
        // mock txManager 가 noop 으로 받아준다.
        TransactionStatus status = new SimpleTransactionStatus();
        lenient().when(txManager.getTransaction(any())).thenReturn(status);

        AppProperties.Challenge cfg = new AppProperties.Challenge(5, 3,
                new AppProperties.Challenge.RankBadge("g", "s", "b"));
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                cfg,
                null, null, null, null, null, null,
                null, null);

        service = new ChallengeAttemptService(
                challengeService, attemptRepository, userRepository, wavHeaderValidator,
                modelServerClient, llmClient, canonicalJson, scoringService, recordingStorage,
                priorAttemptAssembler, statsZoneProvider, settings, props, txManager);

        // registerStorageCleanupOnNonCommit 가 TransactionSynchronizationManager 에 동기화를 등록하므로
        // 테스트 스레드에 동기화 슬롯을 열어둔다. afterCompletion 콜백은 실제 commit 이벤트 없이는 안 돈다.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("submit: audio 가 비면 INVALID_REQUEST")
    void emptyAudioRejects() {
        assertThatThrownBy(() -> service.submit(1L, new byte[0]))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(challengeService, never()).requireActive();
    }

    @Test
    @DisplayName("submit: null audio 도 INVALID_REQUEST")
    void nullAudioRejects() {
        assertThatThrownBy(() -> service.submit(1L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("submit: 챌린지 canonical 이 NULL 이면 CANONICAL_GENERATION_FAILED — admin backfill 안내")
    void missingChallengeCanonicalThrows() {
        DailyChallenge c = challenge(42L, "the event", /* canonical */ null);
        when(challengeService.requireActive()).thenReturn(c);
        when(attemptRepository.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyLong(), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.submit(1L, VALID_WAV))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CANONICAL_GENERATION_FAILED));
        verify(modelServerClient, never()).transcribe(any(), anyString());
    }

    @Test
    @DisplayName("submit: 하루 한도 초과 시 CHALLENGE_ATTEMPT_LIMIT_EXCEEDED")
    void dailyLimitExceeded() {
        DailyChallenge c = challenge(42L, "the", canonicalJsonOf("the", "DH", "AH"));
        when(challengeService.requireActive()).thenReturn(c);
        when(attemptRepository.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyLong(), any(), any())).thenReturn(5L);

        assertThatThrownBy(() -> service.submit(1L, VALID_WAV))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CHALLENGE_ATTEMPT_LIMIT_EXCEEDED));
        verify(modelServerClient, never()).transcribe(any(), anyString());
    }

    @Test
    @DisplayName("submit: cache hit + LLM 응답 → ScoringService 점수로 attempt 영속, isNewBest=true (previousBest=null)")
    void cacheHitHappyPath() {
        DailyChallenge c = challenge(42L, "the event",
                canonicalJsonOf("the", "DH", "AH"));
        when(challengeService.requireActive()).thenReturn(c);
        when(attemptRepository.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyLong(), any(), any())).thenReturn(0L);
        when(modelServerClient.transcribe(any(byte[].class), eq("")))
                .thenReturn(transcribeResult(List.of("DH", "AH", "IH", "V", "EH", "N", "T")));
        LlmStepFeedback feedback = stepFeedback(false);
        when(llmClient.stepFeedback(any(LlmStepContext.class))).thenReturn(feedback);
        when(scoringService.compute(any(), any())).thenReturn(85);
        when(attemptRepository.findUserBestScore(eq(42L), eq(1L))).thenReturn(null);
        User u1 = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(recordingStorage.save(anyLong(), any(byte[].class))).thenReturn("u/test.wav");
        when(attemptRepository.save(any(DailyChallengeAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        ChallengeAttemptResponse response = service.submit(1L, VALID_WAV);

        assertThat(response.score()).isEqualTo(85.0);
        assertThat(response.isMyNewBest()).isTrue();
        assertThat(response.attemptsLimit()).isEqualTo(5);
        assertThat(response.passThreshold()).isEqualTo(75.0);
        verify(scoringService).compute(any(), any());
        verify(attemptRepository).save(any(DailyChallengeAttempt.class));
    }

    @Test
    @DisplayName("submit: previousBest 가 더 크면 isNewBest=false")
    void notNewBestWhenLower() {
        DailyChallenge c = challenge(42L, "the", canonicalJsonOf("the", "DH", "AH"));
        when(challengeService.requireActive()).thenReturn(c);
        when(attemptRepository.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyLong(), any(), any())).thenReturn(0L);
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(transcribeResult(List.of("DH", "AH")));
        when(llmClient.stepFeedback(any(LlmStepContext.class))).thenReturn(stepFeedback(false));
        when(scoringService.compute(any(), any())).thenReturn(70);
        when(attemptRepository.findUserBestScore(eq(42L), eq(1L))).thenReturn(90.0);
        User u1 = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(recordingStorage.save(anyLong(), any(byte[].class))).thenReturn("u/test.wav");
        when(attemptRepository.save(any(DailyChallengeAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        ChallengeAttemptResponse response = service.submit(1L, VALID_WAV);

        assertThat(response.score()).isEqualTo(70.0);
        assertThat(response.isMyNewBest()).isFalse();
    }

    @Test
    @DisplayName("submit: persistAttempt 단계에서 user 미존재면 USER_NOT_FOUND")
    void userMissingThrows() {
        DailyChallenge c = challenge(42L, "the", canonicalJsonOf("the", "DH", "AH"));
        when(challengeService.requireActive()).thenReturn(c);
        when(attemptRepository.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyLong(), any(), any())).thenReturn(0L);
        when(modelServerClient.transcribe(any(byte[].class), anyString()))
                .thenReturn(transcribeResult(List.of("DH", "AH")));
        when(llmClient.stepFeedback(any(LlmStepContext.class))).thenReturn(stepFeedback(false));
        when(scoringService.compute(any(), any())).thenReturn(50);
        when(attemptRepository.findUserBestScore(any(), any())).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(1L, VALID_WAV))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("todayAttempts: statsZoneProvider zone 기준으로 attempt count 호출")
    void todayAttemptsUsesProvidedZone() {
        when(attemptRepository.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(7L), any(), any())).thenReturn(2L);

        long count = service.todayAttempts(7L);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("dailyAttemptLimit getter 가 yaml 의 한도를 그대로 노출한다")
    void dailyLimitGetterExposesConfig() {
        assertThat(service.dailyAttemptLimit()).isEqualTo(5);
    }

    @Test
    @DisplayName("findUserBestScore 는 repository 위임")
    void findUserBestDelegates() {
        when(attemptRepository.findUserBestScore(eq(1L), eq(2L))).thenReturn(99.0);

        assertThat(service.findUserBestScore(1L, 2L)).isEqualTo(99.0);
    }

    // ---------- helpers ----------
    private static DailyChallenge challenge(long id, String text, String canonicalJsonValue) {
        DailyChallenge c = mock(DailyChallenge.class);
        when(c.getId()).thenReturn(id);
        when(c.getTargetText()).thenReturn(text);
        when(c.getCanonicalCachedJson()).thenReturn(canonicalJsonValue);
        return c;
    }

    private static User user(long id) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    // 외부에서 사용하는 캐시 JSON 포맷에 맞게 [{"word":...,"phonemes":[...]},...] 를 만든다.
    // 인자는 (word, [phoneme...]) 쌍의 연속이며, 음소 묶음은 List<String> 으로 전달한다.
    private static String canonicalJsonOf(String word, String... phonemes) {
        com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson cj =
                new com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson(new ObjectMapper());
        return cj.serialize(List.of(
                new com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord(
                        word, List.of(phonemes))));
    }

    private static TranscribeResult transcribeResult(List<String> perceived) {
        return new TranscribeResult(
                perceived,
                perceived.stream().map(p -> 0.9).toList(),
                1.0,
                SpeechRate.NORMAL,
                1.0,
                "echo-baseline",
                "echo");
    }

    private static LlmStepFeedback stepFeedback(boolean retry) {
        return new LlmStepFeedback(
                List.<AlignmentOp>of(),
                List.<LlmPhonemeError>of(),
                retry,
                "ok",
                PronunciationGuide.empty(),
                List.of(), List.of(), List.of(), List.of());
    }
}

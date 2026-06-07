package com.capstoneecho.echo_back.challenge.service;

import com.capstoneecho.echo_back.challenge.dto.ChallengeAttemptResponse;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.entity.DailyChallengeAttempt;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalCacheService;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.modelserver.AnalysisSnapshotFormat;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PriorAttemptAssembler;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

// 사용자가 활성 챌린지에 발음 도전한 결과를 채점하고 저장한다.
// 흐름은 RecordingService 와 같다: canonical 캐시 → /transcribe → LLM step 호출 → 짧은 쓰기 트랜잭션.
@Service
public class ChallengeAttemptService {

    private final ChallengeService challengeService;
    private final DailyChallengeAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final WavHeaderValidator wavHeaderValidator;
    private final ModelServerClient modelServerClient;
    private final LlmClient llmClient;
    private final CanonicalCacheService canonicalCacheService;
    private final RecordingStorage recordingStorage;
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final StatsZoneProvider statsZoneProvider;
    private final RuntimeSettings settings;
    private final int dailyAttemptLimit;
    private final TransactionTemplate writeTx;

    public ChallengeAttemptService(
            ChallengeService challengeService,
            DailyChallengeAttemptRepository attemptRepository,
            UserRepository userRepository,
            WavHeaderValidator wavHeaderValidator,
            ModelServerClient modelServerClient,
            LlmClient llmClient,
            CanonicalCacheService canonicalCacheService,
            RecordingStorage recordingStorage,
            PriorAttemptAssembler priorAttemptAssembler,
            StatsZoneProvider statsZoneProvider,
            RuntimeSettings settings,
            AppProperties appProperties,
            PlatformTransactionManager txManager) {
        this.challengeService = challengeService;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.wavHeaderValidator = wavHeaderValidator;
        this.modelServerClient = modelServerClient;
        this.llmClient = llmClient;
        this.canonicalCacheService = canonicalCacheService;
        this.recordingStorage = recordingStorage;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.statsZoneProvider = statsZoneProvider;
        this.settings = settings;
        AppProperties.Challenge challenge = appProperties.challenge();
        this.dailyAttemptLimit = Math.max(1, challenge == null ? 5 : challenge.dailyAttemptLimit());
        this.writeTx = new TransactionTemplate(txManager);
    }

    public ChallengeAttemptResponse submit(Long userId, byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        wavHeaderValidator.require(audioBytes);

        DailyChallenge challenge = challengeService.requireActive();
        ensureDailyLimit(userId);

        // canonical 조회/생성 — lazy backfill. LLM 실패 시 BusinessException 으로 사용자에게 명시 노출.
        CanonicalResult canonicalResult = canonicalCacheService.resolveForChallenge(challenge.getId());
        List<String> canonicalPhonemes = canonicalResult.flatPhonemes();
        String canonicalSpaceSep = String.join(" ", canonicalPhonemes);

        TranscribeResult transcribe = modelServerClient.transcribe(audioBytes, canonicalSpaceSep);

        // 챌린지도 step 과 동일하게 LLM 단일 호출로 alignment + score + errors 까지 받는다.
        LlmStepContext context = new LlmStepContext(
                "오늘의 챌린지",
                challenge.getTargetText(),
                transcribe.perceived(),
                canonicalPhonemes,
                canonicalResult.words(),
                List.of());
        LlmStepFeedback feedback = llmClient.stepFeedback(context);
        double score = feedback.score();
        Double previousBest = attemptRepository.findUserBestScore(challenge.getId(), userId);
        boolean isNewBest = previousBest == null || score > previousBest;

        return writeTx.execute(status ->
                persistAttempt(userId, challenge, audioBytes, transcribe, canonicalPhonemes,
                        feedback, score, isNewBest));
    }

    private void ensureDailyLimit(Long userId) {
        if (todayAttempts(userId) >= dailyAttemptLimit) {
            throw new BusinessException(ErrorCode.CHALLENGE_ATTEMPT_LIMIT_EXCEEDED);
        }
    }

    public long todayAttempts(Long userId) {
        ZoneId zone = statsZoneProvider.zone();
        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        return attemptRepository
                .countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, start, end);
    }

    public int dailyAttemptLimit() {
        return dailyAttemptLimit;
    }

    @Transactional(readOnly = true)
    public Double findUserBestScore(Long challengeId, Long userId) {
        return attemptRepository.findUserBestScore(challengeId, userId);
    }

    private ChallengeAttemptResponse persistAttempt(
            Long userId,
            DailyChallenge challenge,
            byte[] audioBytes,
            TranscribeResult transcribe,
            List<String> canonicalPhonemes,
            LlmStepFeedback feedback,
            double score,
            boolean isNewBest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String audioPath = recordingStorage.save(userId, audioBytes);
        registerStorageCleanupOnNonCommit(audioPath);

        DailyChallengeAttempt attempt = attemptRepository.save(DailyChallengeAttempt.create(
                challenge,
                user,
                score,
                audioPath,
                AnalysisSnapshotFormat.joinTokens(transcribe.perceived()),
                AnalysisSnapshotFormat.joinTokens(canonicalPhonemes),
                priorAttemptAssembler.toErrorsJson(feedback.errors())));

        long attemptsToday = todayAttempts(userId);
        return new ChallengeAttemptResponse(
                attempt.getId(),
                score,
                settings.passThreshold(),
                isNewBest,
                (int) attemptsToday,
                dailyAttemptLimit,
                transcribe.perceived(),
                canonicalPhonemes,
                feedback.errors());
    }

    private void registerStorageCleanupOnNonCommit(String audioPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    recordingStorage.delete(audioPath);
                }
            }
        });
    }
}

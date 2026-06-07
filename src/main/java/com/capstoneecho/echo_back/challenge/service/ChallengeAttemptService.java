package com.capstoneecho.echo_back.challenge.service;

import com.capstoneecho.echo_back.challenge.dto.ChallengeAttemptResponse;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.entity.DailyChallengeAttempt;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.external.modelserver.AnalysisSnapshotFormat;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PriorAttemptAssembler;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

// 사용자가 활성 챌린지에 발음 도전한 결과를 채점하고 저장한다.
// 흐름은 RecordingService 와 같은 3-단계 (가드 → 외부 호출 → 영속화) 를 따르되, 부모 엔티티가
// DailyChallenge 한 종류라 readTx 가 필요 없다. 채점 정책 (WeightedPerCalculator + ScoringPolicy)
// 은 기존 흐름과 동일하게 재사용해 학습 점수와 챌린지 점수가 같은 의미를 가진다.
@Service
public class ChallengeAttemptService {

    private final ChallengeService challengeService;
    private final DailyChallengeAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final WavHeaderValidator wavHeaderValidator;
    private final ModelServerClient modelServerClient;
    private final ScoringPolicy scoringPolicy;
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
            ScoringPolicy scoringPolicy,
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
        this.scoringPolicy = scoringPolicy;
        this.recordingStorage = recordingStorage;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.statsZoneProvider = statsZoneProvider;
        this.settings = settings;
        AppProperties.Challenge challenge = appProperties.challenge();
        this.dailyAttemptLimit = Math.max(1, challenge == null ? 5 : challenge.dailyAttemptLimit());
        this.writeTx = new TransactionTemplate(txManager);
    }

    // 도전 1회. 가드 통과 후 외부 호출로 점수를 계산하고, 짧은 쓰기 트랜잭션 안에서 audio 저장 + Attempt 영속.
    // best 갱신 여부는 같은 사용자의 직전 best 점수와 비교해 결정한다.
    public ChallengeAttemptResponse submit(Long userId, byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        wavHeaderValidator.require(audioBytes);

        DailyChallenge challenge = challengeService.requireActive();
        ensureDailyLimit(userId);

        G2pResult g2p = modelServerClient.g2p(challenge.getTargetText());
        String canonical = g2p.phonemes() == null ? "" : g2p.phonemes();
        AnalyzeResult analyze = modelServerClient.analyze(audioBytes, canonical);

        double score = scoringPolicy.scoreFromAnalyze(analyze);
        Double previousBest = attemptRepository.findUserBestScore(challenge.getId(), userId);
        boolean isNewBest = previousBest == null || score > previousBest;

        return writeTx.execute(status ->
                persistAttempt(userId, challenge, audioBytes, analyze, score, isNewBest));
    }

    // 오늘의 도전 횟수를 사용자 zone 자정 ~ 다음 자정 범위로 카운트해 일일 상한과 비교한다.
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
            AnalyzeResult analyze,
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
                AnalysisSnapshotFormat.joinTokens(analyze.perceived()),
                AnalysisSnapshotFormat.joinTokens(analyze.canonicalOrEmpty()),
                priorAttemptAssembler.toErrorsJson(analyze.errors())));

        long attemptsToday = todayAttempts(userId);
        return new ChallengeAttemptResponse(
                attempt.getId(),
                score,
                settings.passThreshold(),
                isNewBest,
                (int) attemptsToday,
                dailyAttemptLimit,
                analyze.perceived() == null ? java.util.List.of() : analyze.perceived(),
                analyze.canonicalOrEmpty(),
                analyze.errors() == null ? java.util.List.of() : analyze.errors());
    }

    // 트랜잭션이 롤백되면 디스크 파일도 정리해 orphan 을 남기지 않는다.
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

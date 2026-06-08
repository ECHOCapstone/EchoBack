package com.capstoneecho.echo_back.challenge.service;

import com.capstoneecho.echo_back.challenge.dto.ChallengeAttemptResponse;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.entity.DailyChallengeAttempt;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
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
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringService;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

// 사용자가 활성 챌린지에 발음 도전한 결과를 채점하고 저장한다.
// 흐름은 RecordingService 와 같다: /transcribe → LLM step 호출 (canonical 까지 한 번에) → 짧은 쓰기 트랜잭션.
@Service
public class ChallengeAttemptService {

    private final ChallengeService challengeService;
    private final DailyChallengeAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final WavHeaderValidator wavHeaderValidator;
    private final ModelServerClient modelServerClient;
    private final LlmClient llmClient;
    private final CanonicalJson canonicalJson;
    private final ScoringService scoringService;
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
            CanonicalJson canonicalJson,
            ScoringService scoringService,
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
        this.canonicalJson = canonicalJson;
        this.scoringService = scoringService;
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

        // 챌린지 canonical 은 어드민 등록 시점에 영속화돼 있다. 누락된 chunk 은 채점 실패로 끊는다.
        List<CanonicalWord> canonicalWords = canonicalJson.deserialize(challenge.getCanonicalCachedJson());
        if (canonicalWords.isEmpty()) {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED,
                    "챌린지 canonical 이 비어 있습니다 — 어드민 backfill 이 필요합니다");
        }

        TranscribeResult transcribe = modelServerClient.transcribe(audioBytes, "");

        // Call 2 — 채점 + 피드백. canonical 은 입력으로 전달.
        LlmStepContext context = new LlmStepContext(
                "오늘의 챌린지",
                challenge.getTargetText(),
                canonicalWords,
                transcribe.perceived(),
                List.of());
        LlmStepFeedback feedback = llmClient.stepFeedback(context);
        List<String> canonicalPhonemes = flattenCanonical(canonicalWords);
        double score = scoringService.compute(feedback.alignment(), feedback.errors());

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

    // canonicalWords 의 음소를 단어 순서대로 평탄화한다.
    private static List<String> flattenCanonical(List<CanonicalWord> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        List<String> flat = new ArrayList<>();
        for (CanonicalWord w : words) {
            if (w.phonemes() != null) {
                flat.addAll(w.phonemes());
            }
        }
        return flat;
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

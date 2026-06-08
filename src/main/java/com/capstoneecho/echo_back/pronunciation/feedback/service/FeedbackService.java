package com.capstoneecho.echo_back.pronunciation.feedback.service;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmComprehensiveContext;
import com.capstoneecho.echo_back.external.llm.LlmComprehensiveFeedback;
import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import com.capstoneecho.echo_back.external.llm.LlmRetryContext;
import com.capstoneecho.echo_back.external.llm.LlmRetryFeedback;
import com.capstoneecho.echo_back.external.llm.PracticeItem;
import com.capstoneecho.echo_back.external.llm.PriorAttempt;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.external.modelserver.AnalysisSnapshotFormat;
import com.capstoneecho.echo_back.external.modelserver.PhonemeRecognizer;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.learning.progress.service.ProgressService;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.member.service.MemberService;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackDetailResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackGenerateRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.RetryWordResult;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeOp;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.RetryAttempt;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.RetryAttemptRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PriorAttemptAssembler;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringService;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

// 종합 피드백 생성 / 단어 재시도 흐름. 느린 모델 서버·LLM HTTP 호출 동안 DB 커넥션을 잡지 않도록
// 읽기 → HTTP → 쓰기 세 단계로 트랜잭션을 분리한다. 그 외 단순 조회/완료 토글은 메서드 단위 @Transactional.
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final UserRepository userRepository;
    private final ScriptRepository scriptRepository;
    private final SessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final FeedbackRepository feedbackRepository;
    private final RetryAttemptRepository retryAttemptRepository;
    private final PhonemeRecognizer phonemeRecognizer;
    private final LlmClient llmClient;
    private final LlmCanonicalGenerator canonicalGenerator;
    private final ScoringService scoringService;
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final MemberService memberService;
    private final WavHeaderValidator wavHeaderValidator;
    private final ObjectMapper objectMapper;
    private final RuntimeSettings settings;
    private final ProgressService progressService;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public FeedbackService(
            UserRepository userRepository,
            ScriptRepository scriptRepository,
            SessionRepository sessionRepository,
            RecordingRepository recordingRepository,
            FeedbackRepository feedbackRepository,
            RetryAttemptRepository retryAttemptRepository,
            PhonemeRecognizer phonemeRecognizer,
            LlmClient llmClient,
            LlmCanonicalGenerator canonicalGenerator,
            ScoringService scoringService,
            PriorAttemptAssembler priorAttemptAssembler,
            MemberService memberService,
            WavHeaderValidator wavHeaderValidator,
            ObjectMapper objectMapper,
            RuntimeSettings settings,
            ProgressService progressService,
            PlatformTransactionManager txManager) {
        this.userRepository = userRepository;
        this.scriptRepository = scriptRepository;
        this.sessionRepository = sessionRepository;
        this.recordingRepository = recordingRepository;
        this.feedbackRepository = feedbackRepository;
        this.retryAttemptRepository = retryAttemptRepository;
        this.phonemeRecognizer = phonemeRecognizer;
        this.llmClient = llmClient;
        this.canonicalGenerator = canonicalGenerator;
        this.scoringService = scoringService;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.memberService = memberService;
        this.wavHeaderValidator = wavHeaderValidator;
        this.objectMapper = objectMapper;
        this.settings = settings;
        this.progressService = progressService;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    public FeedbackDetailResponse generate(Long userId, FeedbackGenerateRequest request) {
        if (request == null
                || request.recordingIds() == null
                || request.recordingIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        boolean hasScript = request.scriptId() != null;
        boolean hasSession = request.sessionId() != null;
        if (hasScript == hasSession) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<Long> recordingIds = request.recordingIds();

        // 멱등 키 = (script|session):parentId + 정렬된 recordingIds 의 SHA-256. 같은 녹음 집합으로 generate 를
        // 반복 호출해도 피드백이 한 개만 생기게 한다 (각 피드백을 complete 해 경험치를 복제하던 우회 차단).
        Long parentId = hasScript ? request.scriptId() : request.sessionId();
        String recordingIdsHash = idempotencyKey(hasScript, parentId, recordingIds);

        // 빠른 경로: 같은 (user, 녹음 집합) 으로 이미 만든 피드백이 있으면 비싼 LLM 호출 없이 그대로 반환.
        FeedbackDetailResponse cached = readTx.execute(status ->
                feedbackRepository.findByUser_IdAndRecordingIdsHash(userId, recordingIdsHash)
                        .map(fb -> FeedbackDetailResponse.from(fb, objectMapper))
                        .orElse(null));
        if (cached != null) {
            return cached;
        }

        // (1) 읽기 단계: 녹음 일괄 조회·검증 + 집계 (약점 음소·step 요약) 를 트랜잭션 안에서 끝낸다.
        GeneratePlan plan = readTx.execute(status -> {
            userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (hasScript) {
                Script script = scriptRepository.findById(request.scriptId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
                List<Recording> recordings = recordingRepository
                        .findAllByUser_IdAndScript_IdAndIdInOrderByCreatedAtAsc(
                                userId, script.getId(), recordingIds);
                requireFullMatch(recordings, recordingIds);
                return new GeneratePlan(true, script.getTitle(), script.getContent(),
                        aggregate(recordings), seededPracticeWord(script));
            }
            Session session = sessionRepository.findByIdAndUser_Id(request.sessionId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
            List<Recording> recordings = recordingRepository
                    .findAllByUser_IdAndSession_IdAndIdInOrderByCreatedAtAsc(
                            userId, session.getId(), recordingIds);
            requireFullMatch(recordings, recordingIds);
            return new GeneratePlan(false, session.getTitle(), session.getScriptText(),
                    aggregate(recordings), null);
        });

        // (2) HTTP 단계: 트랜잭션 밖에서 종합 피드백 LLM 호출.
        LlmComprehensiveFeedback llm =
                callComprehensive(plan.chapterTitle(), plan.chapterContent(), plan.aggregated());

        // (3) 쓰기 단계: 부모 엔티티를 다시 조회해 PronunciationFeedback 을 영속화.
        try {
            return writeTx.execute(status -> {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                String practiceWord = pickPracticeWord(plan.seededPracticeWord(), llm);
                Aggregated aggregated = plan.aggregated();
                PronunciationFeedback feedback;
                if (plan.hasScript()) {
                    Script script = scriptRepository.findById(request.scriptId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
                    feedback = PronunciationFeedback.forScript(
                            user, script, plan.chapterTitle(), aggregated.accuracy(),
                            aggregated.weakPhoneme(), practiceWord, llm.summaryKr());
                } else {
                    Session session = sessionRepository.findByIdAndUser_Id(request.sessionId(), userId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
                    feedback = PronunciationFeedback.forSession(
                            user, session, plan.chapterTitle(), aggregated.accuracy(),
                            aggregated.weakPhoneme(), practiceWord, llm.summaryKr());
                }
                feedback.assignRecordingIdsHash(recordingIdsHash);
                applyComprehensive(feedback, llm);
                attachAggregatedErrors(feedback, aggregated.aggregatedErrors());
                PronunciationFeedback saved = feedbackRepository.save(feedback);
                return FeedbackDetailResponse.from(saved, objectMapper);
            });
        } catch (DataIntegrityViolationException e) {
            // 동시 generate 레이스: 다른 요청이 같은 (user, 녹음 집합) 으로 먼저 저장해 유니크 제약에 걸렸다.
            // 빠른 경로에서 못 본 그 피드백을 다시 읽어 반환한다. 해시 충돌이 아닌 다른 무결성 오류면 그대로 전파.
            return readTx.execute(status ->
                    feedbackRepository.findByUser_IdAndRecordingIdsHash(userId, recordingIdsHash)
                            .map(fb -> FeedbackDetailResponse.from(fb, objectMapper))
                            .orElseThrow(() -> e));
        }
    }

    // generate 멱등 키. recordingIds 는 순서 무관하므로 정렬해 같은 집합이면 같은 해시가 나오게 한다.
    // scriptId/sessionId 를 prefix 로 섞어 서로 다른 콘텐츠가 우연히 같은 해시를 갖는 일을 막는다.
    private static String idempotencyKey(boolean hasScript, Long parentId, List<Long> recordingIds) {
        List<Long> sorted = new ArrayList<>(recordingIds);
        sorted.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        String raw = (hasScript ? "script:" : "session:") + parentId + "|" + sorted;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record GeneratePlan(
            boolean hasScript, String chapterTitle, String chapterContent,
            Aggregated aggregated, String seededPracticeWord) {}

    public RetryWordResult retryWord(Long userId, Long feedbackId, byte[] audioBytes) {
        return retryWord(userId, feedbackId, audioBytes, null);
    }

    // 단어/구 재시도. canonical 은 매 호출마다 LlmCanonicalGenerator.generate(word) 로 즉석 생성한다
    // (단어 단위 콘텐츠 캐시 없음). 이후 LlmClient.retryFeedback 호출과 ScoringService 채점이 이어진다.
    public RetryWordResult retryWord(
            Long userId, Long feedbackId, byte[] audioBytes, String overrideWord) {
        wavHeaderValidator.require(audioBytes);

        RetryPlan plan = readTx.execute(status -> {
            PronunciationFeedback feedback = feedbackRepository.findByIdAndUser_Id(feedbackId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
            String word = resolveRetryWord(feedback, overrideWord);
            Pageable cap = PageRequest.of(0, settings.priorAttemptsCap());
            List<RetryAttempt> recent = retryAttemptRepository
                    .findByFeedback_IdOrderByCreatedAtDesc(feedbackId, cap);
            return new RetryPlan(word, priorAttemptAssembler.fromRetries(recent));
        });

        // 단어/구 즉석 재시도는 콘텐츠 캐시가 없으므로 매번 canonical 을 LLM 으로 생성한다.
        // canonical 을 요구하는 모델(FiLM)은 이를 변조 조건으로 주입하고(순차), 그렇지 않은 모델은
        // /transcribe 를 canonical 생성과 병렬 실행한다.
        PhonemeRecognizer.Recognized recognized = phonemeRecognizer.recognize(
                audioBytes, () -> canonicalGenerator.generate(plan.word()).words());
        List<CanonicalWord> canonicalWords = recognized.canonicalWords();
        List<String> canonicalPhonemes = recognized.canonicalPhonemes();
        List<String> perceived = recognized.transcribe().perceived();

        LlmRetryContext context = new LlmRetryContext(
                plan.word(),
                canonicalWords,
                perceived,
                plan.priorAttempts());
        LlmRetryFeedback llm = llmClient.retryFeedback(context);
        int score = scoringService.compute(llm.alignment(), llm.errors());
        boolean passed = score >= settings.passThreshold();

        int finalScore = score;
        writeTx.executeWithoutResult(status -> {
            PronunciationFeedback feedback = feedbackRepository.findByIdAndUser_Id(feedbackId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
            persistRetryAttempt(feedback, plan.word(), perceived, canonicalPhonemes, llm, finalScore);
        });

        return new RetryWordResult(
                llm.correct(),
                passed,
                llm.retryRecommended(),
                perceived,
                canonicalPhonemes,
                (double) score,
                llm.guidanceKr(),
                llm.phonemeTips());
    }

    private record RetryPlan(String word, List<PriorAttempt> priorAttempts) {}

    private String resolveRetryWord(PronunciationFeedback feedback, String overrideWord) {
        if (overrideWord != null && !overrideWord.isBlank()) {
            return overrideWord;
        }
        String word = feedback.getPracticeWord();
        if (word == null || word.isBlank()) {
            return settings.defaultPracticeWord();
        }
        return word;
    }

    private void persistRetryAttempt(
            PronunciationFeedback feedback,
            String word,
            List<String> perceived,
            List<String> canonical,
            LlmRetryFeedback llm,
            int score) {
        RetryAttempt attempt = RetryAttempt.create(
                feedback,
                word,
                AnalysisSnapshotFormat.joinTokens(perceived),
                AnalysisSnapshotFormat.joinTokens(canonical),
                priorAttemptAssembler.toErrorsJson(llm.errors()),
                (double) score,
                llm.guidanceKr(),
                llm.correct());
        retryAttemptRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<FeedbackSummaryResponse> list(Long userId) {
        return feedbackRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(FeedbackSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeedbackDetailResponse get(Long userId, Long feedbackId) {
        PronunciationFeedback feedback = feedbackRepository.findByIdAndUser_Id(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        return FeedbackDetailResponse.from(feedback, objectMapper);
    }

    public UserResponse complete(Long userId, Long feedbackId) {
        CompletionOutcome outcome = writeTx.execute(status -> {
            PronunciationFeedback feedback = feedbackRepository
                    .findByIdAndUser_Id(feedbackId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
            Long scriptId = feedback.getScript() == null ? null : feedback.getScript().getId();
            Long sessionId = feedback.getSession() == null ? null : feedback.getSession().getId();

            Instant now = Instant.now();
            int affected = feedbackRepository.markCompletedAtomically(feedbackId, userId, now);
            if (affected == 1) {
                resetProgressForFeedback(userId, scriptId, sessionId);
                return new CompletionOutcome(true, null);
            }
            if (!feedback.isCompleted()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            return new CompletionOutcome(false, UserResponse.from(user));
        });

        if (outcome.awarded()) {
            return memberService.awardCompletionRewards(userId, settings.completionExp());
        }
        return outcome.fallbackUserResponse();
    }

    private record CompletionOutcome(boolean awarded, UserResponse fallbackUserResponse) {}

    private void resetProgressForFeedback(Long userId, Long scriptId, Long sessionId) {
        // 부모 콘텐츠가 이미 사라진 (V23 SET NULL) 피드백은 진행 상태를 되돌릴 대상이 없다 — 명시 로깅 후 skip.
        if (scriptId == null && sessionId == null) {
            log.info("feedback complete: 부모 script/session 모두 NULL — progress reset 생략 (user={})", userId);
            return;
        }
        if (scriptId != null) {
            progressService.resetChapter(userId, scriptId);
        }
        if (sessionId != null) {
            progressService.resetSession(userId, sessionId);
        }
    }

    private void requireFullMatch(List<Recording> found, List<Long> requested) {
        if (found.size() != requested.size()) {
            throw new BusinessException(ErrorCode.RECORDING_NOT_FOUND);
        }
    }

    // 챕터 누적 통계 + step 별 best 점수 / 시도 횟수 / 약점 음소 요약을 만든다.
    // Recording.errors_json (LlmPhonemeError 리스트) 를 역직렬화해 모든 음소 오류를 누적한다.
    private Aggregated aggregate(List<Recording> recordings) {
        double accuracy = averageScore(recordings);
        List<LlmPhonemeError> aggregatedErrors = new ArrayList<>();
        Map<Long, BestPerStep> bestByStep = new LinkedHashMap<>();
        for (Recording r : recordings) {
            List<LlmPhonemeError> errs = priorAttemptAssembler.parseErrors(r.getErrorsJson());
            aggregatedErrors.addAll(errs);
            Long stepId = r.getStep() == null ? null : r.getStep().getId();
            String targetText = r.getTargetTextSnapshot() == null ? "" : r.getTargetTextSnapshot();
            String stepWeak = firstCanonicalPhoneme(errs);
            double score = r.getStepScore() == null ? 0.0 : r.getStepScore();
            bestByStep.merge(
                    stepId == null ? -1L * (bestByStep.size() + 1) : stepId,
                    new BestPerStep(targetText, 1, score, stepWeak),
                    BestPerStep::merge);
        }
        String dominantWeak = mostFrequentCanonical(aggregatedErrors);
        List<LlmComprehensiveContext.StepSummary> stepSummaries = bestByStep.values().stream()
                .map(b -> new LlmComprehensiveContext.StepSummary(
                        b.targetText(), b.attempts(), b.bestScore(), b.weakPhoneme()))
                .toList();
        return new Aggregated(accuracy, dominantWeak, aggregatedErrors, stepSummaries);
    }

    // 녹음 점수 평균. 점수가 한 건도 없으면 0 — LLM 채점 실패 시 만점 회귀 방지.
    private static double averageScore(List<Recording> recordings) {
        if (recordings == null || recordings.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (Recording r : recordings) {
            Double s = r.getStepScore();
            if (s != null) {
                sum += Math.max(0.0, Math.min(100.0, s));
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    // step 한 건의 대표 약점 음소 — errors 의 첫 canonical 비-공백.
    private static String firstCanonicalPhoneme(List<LlmPhonemeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        for (LlmPhonemeError e : errors) {
            String c = e.canonical();
            if (c != null && !c.isBlank()) {
                return c.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    // 챕터 종합 — 누적 errors 의 canonical 빈도 최댓값.
    private static String mostFrequentCanonical(List<LlmPhonemeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LlmPhonemeError e : errors) {
            String c = e.canonical();
            if (c == null || c.isBlank()) {
                continue;
            }
            counts.merge(c.trim().toUpperCase(Locale.ROOT), 1, Integer::sum);
        }
        String top = null;
        int topCount = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > topCount) {
                topCount = entry.getValue();
                top = entry.getKey();
            }
        }
        return top;
    }

    private LlmComprehensiveFeedback callComprehensive(
            String chapterTitle, String chapterContent, Aggregated aggregated) {
        LlmComprehensiveContext context = new LlmComprehensiveContext(
                chapterTitle,
                chapterContent,
                aggregated.stepSummaries(),
                aggregated.aggregatedErrors(),
                aggregated.weakPhoneme(),
                aggregated.accuracy());
        return llmClient.comprehensiveFeedback(context);
    }

    private void applyComprehensive(PronunciationFeedback feedback, LlmComprehensiveFeedback llm) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("strengths", llm.strengths());
        payload.put("weaknesses", llm.weaknesses());
        payload.put("nextPracticeItems", llm.nextPracticeItems());
        feedback.applyComprehensiveJson(objectMapper.writeValueAsString(payload));
    }

    private String seededPracticeWord(Script script) {
        String seeded = script.getPracticeWord();
        return (seeded != null && !seeded.isBlank()) ? seeded : null;
    }

    private String pickPracticeWord(String seeded, LlmComprehensiveFeedback llm) {
        if (seeded != null && !seeded.isBlank()) {
            return seeded;
        }
        for (PracticeItem item : llm.nextPracticeItems()) {
            if (item.kind() == PracticeItem.Kind.WORD && !item.text().isBlank()) {
                return item.text();
            }
        }
        for (PracticeItem item : llm.nextPracticeItems()) {
            if (!item.text().isBlank()) {
                return item.text();
            }
        }
        return settings.defaultPracticeWord();
    }

    private void attachAggregatedErrors(
            PronunciationFeedback feedback, List<LlmPhonemeError> errors) {
        for (LlmPhonemeError e : errors) {
            PhonemeOp op = mapOp(e.op());
            if (op == null) {
                continue;
            }
            feedback.recordPhonemeError(op, e.canonical(), e.perceived(), e.canonicalIndex());
        }
    }

    private static PhonemeOp mapOp(AlignmentOp.ErrorType op) {
        if (op == null) {
            return null;
        }
        return switch (op) {
            case SUBSTITUTION -> PhonemeOp.SUB;
            case DELETION -> PhonemeOp.DEL;
            case INSERTION -> PhonemeOp.INS;
            case MATCH -> null;
        };
    }

    private record Aggregated(
            double accuracy,
            String weakPhoneme,
            List<LlmPhonemeError> aggregatedErrors,
            List<LlmComprehensiveContext.StepSummary> stepSummaries
    ) {}

    private record BestPerStep(String targetText, int attempts, double bestScore, String weakPhoneme) {
        BestPerStep merge(BestPerStep other) {
            int totalAttempts = this.attempts + other.attempts;
            double best = Math.max(this.bestScore, other.bestScore);
            String weak = other.weakPhoneme == null ? this.weakPhoneme : other.weakPhoneme;
            String text = this.targetText.isBlank() ? other.targetText : this.targetText;
            return new BestPerStep(text, totalAttempts, best, weak);
        }
    }
}

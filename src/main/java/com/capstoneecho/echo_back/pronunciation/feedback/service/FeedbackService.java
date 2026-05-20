package com.capstoneecho.echo_back.pronunciation.feedback.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmComprehensiveContext;
import com.capstoneecho.echo_back.external.llm.LlmComprehensiveFeedback;
import com.capstoneecho.echo_back.external.llm.LlmRetryContext;
import com.capstoneecho.echo_back.external.llm.LlmRetryFeedback;
import com.capstoneecho.echo_back.external.llm.PracticeItem;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
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
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.feedback.support.WeakPhonemeAnalyzer;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final UserRepository userRepository;
    private final ScriptRepository scriptRepository;
    private final SessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final FeedbackRepository feedbackRepository;
    private final RetryAttemptRepository retryAttemptRepository;
    private final ModelServerClient modelServerClient;
    private final LlmClient llmClient;
    private final ScoringPolicy scoringPolicy;
    private final WeakPhonemeAnalyzer weakPhonemeAnalyzer;
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final MemberService memberService;
    private final WavHeaderValidator wavHeaderValidator;
    private final ObjectMapper objectMapper;
    private final int completionExp;
    private final double passThreshold;
    private final int priorAttemptsCap;
    private final String defaultPracticeWord;

    public FeedbackService(
            UserRepository userRepository,
            ScriptRepository scriptRepository,
            SessionRepository sessionRepository,
            RecordingRepository recordingRepository,
            FeedbackRepository feedbackRepository,
            RetryAttemptRepository retryAttemptRepository,
            ModelServerClient modelServerClient,
            LlmClient llmClient,
            ScoringPolicy scoringPolicy,
            WeakPhonemeAnalyzer weakPhonemeAnalyzer,
            PriorAttemptAssembler priorAttemptAssembler,
            MemberService memberService,
            WavHeaderValidator wavHeaderValidator,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.scriptRepository = scriptRepository;
        this.sessionRepository = sessionRepository;
        this.recordingRepository = recordingRepository;
        this.feedbackRepository = feedbackRepository;
        this.retryAttemptRepository = retryAttemptRepository;
        this.modelServerClient = modelServerClient;
        this.llmClient = llmClient;
        this.scoringPolicy = scoringPolicy;
        this.weakPhonemeAnalyzer = weakPhonemeAnalyzer;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.memberService = memberService;
        this.wavHeaderValidator = wavHeaderValidator;
        this.objectMapper = objectMapper;
        AppProperties.Gamification g = appProperties.gamification();
        this.completionExp = g == null ? 10 : g.completionExp();
        this.passThreshold = g == null ? 80.0 : g.passThreshold();
        this.priorAttemptsCap = g == null ? 10 : Math.max(1, g.priorAttemptsCap());
        this.defaultPracticeWord = g == null ? "the" : g.defaultPracticeWord();
    }

    // 챕터 종합 피드백 생성:
    // (1) recordingIds 로 같은 script (또는 session) 의 녹음들 일괄 조회
    // (2) Recording.errors_json 을 역직렬화해 chapter 전체 약점 음소 빈도 계산 (weakPhoneme)
    // (3) step 별 best 점수 + 시도 횟수 요약 → LLM comprehensiveFeedback 호출 (구조화 출력)
    // (4) LLM 결과의 strengths / weaknesses / nextPracticeItems 를 JSON 으로 캐싱
    // (5) PronunciationFeedback 엔티티 저장 + 응답
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> recordingIds = request.recordingIds();

        List<Recording> recordings;
        String chapterTitle;
        String chapterContent;
        PronunciationFeedback feedback;
        if (hasScript) {
            Script script = scriptRepository.findById(request.scriptId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
            recordings = recordingRepository
                    .findAllByUser_IdAndScript_IdAndIdInOrderByCreatedAtAsc(
                            userId, script.getId(), recordingIds);
            requireFullMatch(recordings, recordingIds);
            chapterTitle = script.getTitle();
            chapterContent = script.getContent();
            Aggregated aggregated = aggregate(recordings);
            LlmComprehensiveFeedback llm = callComprehensive(
                    chapterTitle, chapterContent, aggregated);
            String practiceWord = pickPracticeWord(script, llm);
            feedback = PronunciationFeedback.forScript(
                    user, script, chapterTitle, aggregated.accuracy(),
                    aggregated.weakPhoneme(), practiceWord, llm.summaryKr());
            applyComprehensive(feedback, llm);
            attachAggregatedErrors(feedback, recordings);
        } else {
            Session session = sessionRepository.findByIdAndUser_Id(request.sessionId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
            recordings = recordingRepository
                    .findAllByUser_IdAndSession_IdAndIdInOrderByCreatedAtAsc(
                            userId, session.getId(), recordingIds);
            requireFullMatch(recordings, recordingIds);
            chapterTitle = session.getTitle();
            chapterContent = session.getScriptText();
            Aggregated aggregated = aggregate(recordings);
            LlmComprehensiveFeedback llm = callComprehensive(
                    chapterTitle, chapterContent, aggregated);
            String practiceWord = pickPracticeWord(null, llm);
            feedback = PronunciationFeedback.forSession(
                    user, session, chapterTitle, aggregated.accuracy(),
                    aggregated.weakPhoneme(), practiceWord, llm.summaryKr());
            applyComprehensive(feedback, llm);
            attachAggregatedErrors(feedback, recordings);
        }

        PronunciationFeedback saved = feedbackRepository.save(feedback);
        return FeedbackDetailResponse.from(saved, objectMapper);
    }

    // 단어 / 구 재시도 흐름:
    // (1) WAV 검증 → feedback 조회 → 연습 단어 (또는 클라이언트가 명시한 word) 결정
    // (2) 모델 서버 g2p / analyze
    // (3) 같은 feedback 의 이전 retry-word 시도들을 RetryAttempt 에서 불러와 priorAttempts 로 채운다
    // (4) LLM 구조화 결과로 응답 구성
    // (5) 이번 시도를 RetryAttempt 로 저장해 다음 호출의 누적 컨텍스트가 된다
    public RetryWordResult retryWord(Long userId, Long feedbackId, byte[] audioBytes) {
        return retryWord(userId, feedbackId, audioBytes, null);
    }

    public RetryWordResult retryWord(
            Long userId, Long feedbackId, byte[] audioBytes, String overrideWord) {
        wavHeaderValidator.require(audioBytes);
        PronunciationFeedback feedback = feedbackRepository.findByIdAndUser_Id(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));

        String word = resolveRetryWord(feedback, overrideWord);

        G2pResult g2p = modelServerClient.g2p(word);
        String canonical = g2p == null || g2p.phonemes() == null ? "" : g2p.phonemes();
        AnalyzeResult analyze = modelServerClient.analyze(audioBytes, canonical);

        List<String> perceived = analyze.perceived() == null ? List.of() : analyze.perceived();
        List<String> canonicalPhonemes = analyze.canonicalOrEmpty();
        double score = scoringPolicy.singleWordScore(analyze);

        Pageable cap = PageRequest.of(0, priorAttemptsCap);
        List<RetryAttempt> recent = retryAttemptRepository
                .findByFeedback_IdOrderByCreatedAtDesc(feedback.getId(), cap);

        LlmRetryContext context = new LlmRetryContext(
                word,
                perceived,
                canonicalPhonemes,
                analyze.errors(),
                feedback.getWeakPhoneme(),
                score,
                priorAttemptAssembler.fromRetries(recent));
        LlmRetryFeedback llm = llmClient.retryFeedback(context);
        boolean passed = score >= passThreshold && !llm.retryRecommended();

        persistRetryAttempt(feedback, word, perceived, canonicalPhonemes, analyze, score, llm);

        return new RetryWordResult(
                llm.correct(),
                passed,
                llm.retryRecommended(),
                perceived,
                canonicalPhonemes,
                score,
                llm.guidanceKr(),
                llm.phonemeTips());
    }

    // overrideWord 가 들어오면 우선 — 종합 피드백의 nextPracticeItems 중 어떤 항목을 재시도할지 클라이언트가 명시한다.
    // 없으면 feedback 의 practiceWord, 둘 다 없으면 외부화된 폴백 단어로 떨어진다.
    private String resolveRetryWord(PronunciationFeedback feedback, String overrideWord) {
        if (overrideWord != null && !overrideWord.isBlank()) {
            return overrideWord;
        }
        String word = feedback.getPracticeWord();
        if (word == null || word.isBlank()) {
            return defaultPracticeWord;
        }
        return word;
    }

    private void persistRetryAttempt(
            PronunciationFeedback feedback,
            String word,
            List<String> perceived,
            List<String> canonical,
            AnalyzeResult analyze,
            double score,
            LlmRetryFeedback llm) {
        String errorsJson = serializeErrors(analyze.errors());
        RetryAttempt attempt = RetryAttempt.create(
                feedback,
                word,
                joinTokens(perceived),
                joinTokens(canonical),
                errorsJson,
                score,
                llm.guidanceKr(),
                llm.correct());
        retryAttemptRepository.save(attempt);
    }

    private String serializeErrors(List<AnalyzeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (RuntimeException ex) {
            log.warn("Failed to serialize retry errors; storing NULL", ex);
            return null;
        }
    }

    private static String joinTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return null;
        return String.join(" ", tokens);
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

    // 완료 토글: 동시에 두 요청이 들어와도 정확히 한 번만 EXP 가 지급되도록 원자 UPDATE 후 분기.
    public UserResponse complete(Long userId, Long feedbackId) {
        Instant now = Instant.now();
        int affected = feedbackRepository.markCompletedAtomically(feedbackId, userId, now);
        if (affected == 1) {
            return memberService.awardCompletionRewards(userId, completionExp);
        }
        PronunciationFeedback existing = feedbackRepository
                .findByIdAndUser_Id(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        if (!existing.isCompleted()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    private void requireFullMatch(List<Recording> found, List<Long> requested) {
        if (found.size() != requested.size()) {
            throw new BusinessException(ErrorCode.RECORDING_NOT_FOUND);
        }
    }

    // 챕터 누적 통계 + step 별 best 점수 / 시도 횟수 / 약점 음소 요약을 만든다.
    // Recording.errors_json 을 역직렬화해 모든 음소 오류를 누적한다.
    private Aggregated aggregate(List<Recording> recordings) {
        double accuracy = scoringPolicy.aggregate(recordings);
        List<AnalyzeError> aggregatedErrors = new ArrayList<>();
        Map<Long, BestPerStep> bestByStep = new LinkedHashMap<>();
        for (Recording r : recordings) {
            List<AnalyzeError> errs = priorAttemptAssembler.parseErrors(r.getErrorsJson());
            aggregatedErrors.addAll(errs);
            Long stepId = r.getStep() == null ? null : r.getStep().getId();
            String targetText = r.getTargetTextSnapshot() == null ? "" : r.getTargetTextSnapshot();
            String stepWeak = pickWeak(errs);
            double score = r.getStepScore() == null ? 0.0 : r.getStepScore();
            bestByStep.merge(
                    stepId == null ? -1L * (bestByStep.size() + 1) : stepId,
                    new BestPerStep(targetText, 1, score, stepWeak),
                    BestPerStep::merge);
        }
        String dominantWeak = weakPhonemeAnalyzer.topOneFromErrors(aggregatedErrors);
        List<LlmComprehensiveContext.StepSummary> stepSummaries = bestByStep.values().stream()
                .map(b -> new LlmComprehensiveContext.StepSummary(
                        b.targetText(), b.attempts(), b.bestScore(), b.weakPhoneme()))
                .toList();
        return new Aggregated(accuracy, dominantWeak, aggregatedErrors, stepSummaries);
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
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("strengths", llm.strengths());
            payload.put("weaknesses", llm.weaknesses());
            payload.put("nextPracticeItems", llm.nextPracticeItems());
            feedback.applyComprehensiveJson(objectMapper.writeValueAsString(payload));
        } catch (RuntimeException ex) {
            log.warn("Failed to serialize comprehensive feedback; storing NULL", ex);
        }
    }

    // 챕터에 미리 박힌 단어 → LLM 추천 첫 항목 → 외부화된 폴백 단어 순.
    private String pickPracticeWord(Script script, LlmComprehensiveFeedback llm) {
        if (script != null) {
            String seeded = script.getPracticeWord();
            if (seeded != null && !seeded.isBlank()) {
                return seeded;
            }
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
        return defaultPracticeWord;
    }

    // 챕터의 모든 시도에서 모인 음소 오류를 PhonemeError 자식으로 저장한다.
    private void attachAggregatedErrors(
            PronunciationFeedback feedback, List<Recording> recordings) {
        for (Recording r : recordings) {
            for (AnalyzeError e : priorAttemptAssembler.parseErrors(r.getErrorsJson())) {
                PhonemeOp op = mapOp(e.op());
                if (op == null) {
                    continue;
                }
                feedback.recordPhonemeError(op, e.canonical(), e.perceived(), e.canonicalIndex());
            }
        }
    }

    private static String pickWeak(List<AnalyzeError> errors) {
        if (errors == null) return null;
        for (AnalyzeError e : errors) {
            if (e.canonical() != null && !e.canonical().isBlank()) return e.canonical();
        }
        return null;
    }

    private static PhonemeOp mapOp(String op) {
        if (op == null) {
            return null;
        }
        String upper = op.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "SUB", "SUBSTITUTION" -> PhonemeOp.SUB;
            case "DEL", "DELETION" -> PhonemeOp.DEL;
            case "INS", "INSERTION" -> PhonemeOp.INS;
            default -> null;
        };
    }

    private record Aggregated(
            double accuracy,
            String weakPhoneme,
            List<AnalyzeError> aggregatedErrors,
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

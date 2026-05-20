package com.capstoneecho.echo_back.pronunciation.feedback.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
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
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PracticeWordResolver;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PronunciationPromptBuilder;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.feedback.support.WeakPhonemeAnalyzer;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FeedbackService {

    private final UserRepository userRepository;
    private final ScriptRepository scriptRepository;
    private final SessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final FeedbackRepository feedbackRepository;
    private final ModelServerClient modelServerClient;
    private final LlmClient llmClient;
    private final ScoringPolicy scoringPolicy;
    private final WeakPhonemeAnalyzer weakPhonemeAnalyzer;
    private final PracticeWordResolver practiceWordResolver;
    private final PronunciationPromptBuilder promptBuilder;
    private final MemberService memberService;
    private final WavHeaderValidator wavHeaderValidator;
    private final int completionExp;
    private final String feedbackFallback;
    private final String retryFallback;

    public FeedbackService(
            UserRepository userRepository,
            ScriptRepository scriptRepository,
            SessionRepository sessionRepository,
            RecordingRepository recordingRepository,
            FeedbackRepository feedbackRepository,
            ModelServerClient modelServerClient,
            LlmClient llmClient,
            ScoringPolicy scoringPolicy,
            WeakPhonemeAnalyzer weakPhonemeAnalyzer,
            PracticeWordResolver practiceWordResolver,
            PronunciationPromptBuilder promptBuilder,
            MemberService memberService,
            WavHeaderValidator wavHeaderValidator,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.scriptRepository = scriptRepository;
        this.sessionRepository = sessionRepository;
        this.recordingRepository = recordingRepository;
        this.feedbackRepository = feedbackRepository;
        this.modelServerClient = modelServerClient;
        this.llmClient = llmClient;
        this.scoringPolicy = scoringPolicy;
        this.weakPhonemeAnalyzer = weakPhonemeAnalyzer;
        this.practiceWordResolver = practiceWordResolver;
        this.promptBuilder = promptBuilder;
        this.memberService = memberService;
        this.wavHeaderValidator = wavHeaderValidator;
        AppProperties.Gamification g = appProperties.gamification();
        this.completionExp = g == null ? 10 : g.completionExp();
        AppProperties.Messages m = appProperties.messages();
        this.feedbackFallback = m == null ? "" : m.feedbackGuidanceFallback();
        this.retryFallback = m == null ? "" : m.retryGuidanceFallback();
    }

    // 학습 단위 피드백 1건을 생성한다. recordingIds 는 같은 script 또는 같은 session 의 자식만 모인다.
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

        PronunciationFeedback feedback;
        if (hasScript) {
            Script script = scriptRepository.findById(request.scriptId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
            List<Recording> recordings = recordingRepository
                    .findAllByUser_IdAndScript_IdAndIdInOrderByCreatedAtAsc(
                            userId, script.getId(), recordingIds);
            requireFullMatch(recordings, recordingIds);
            Aggregated aggregated = aggregate(recordings);
            String practiceWord = practiceWordResolver.resolve(
                    script, aggregated.weakPhoneme(), aggregated.context());
            String guidance = safeSummarizeFeedback(aggregated.context());
            feedback = PronunciationFeedback.forScript(
                    user, script, script.getTitle(), aggregated.accuracy(),
                    aggregated.weakPhoneme(), practiceWord, guidance);
        } else {
            Session session = sessionRepository.findByIdAndUser_Id(request.sessionId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
            List<Recording> recordings = recordingRepository
                    .findAllByUser_IdAndSession_IdAndIdInOrderByCreatedAtAsc(
                            userId, session.getId(), recordingIds);
            requireFullMatch(recordings, recordingIds);
            Aggregated aggregated = aggregate(recordings);
            String practiceWord = practiceWordResolver.resolve(
                    null, aggregated.weakPhoneme(), aggregated.context());
            String guidance = safeSummarizeFeedback(aggregated.context());
            feedback = PronunciationFeedback.forSession(
                    user, session, session.getTitle(), aggregated.accuracy(),
                    aggregated.weakPhoneme(), practiceWord, guidance);
        }

        PronunciationFeedback saved = feedbackRepository.save(feedback);
        return FeedbackDetailResponse.from(saved);
    }

    // 한 단어 재시도. WAV 검증 → G2P → 분석 → 점수 및 가이드 산출.
    public RetryWordResult retryWord(Long userId, Long feedbackId, byte[] audioBytes) {
        wavHeaderValidator.require(audioBytes);
        PronunciationFeedback feedback = feedbackRepository.findByIdAndUser_Id(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));

        String word = feedback.getPracticeWord();
        if (word == null || word.isBlank()) {
            word = practiceWordResolver.defaultWord();
        }

        G2pResult g2p = modelServerClient.g2p(word);
        String canonical = g2p == null || g2p.phonemes() == null ? "" : g2p.phonemes();
        AnalyzeResult analyze = modelServerClient.analyze(audioBytes, canonical);

        List<String> perceived = analyze.perceived() == null ? List.of() : analyze.perceived();
        List<String> canonicalPhonemes = analyze.canonicalOrEmpty();
        boolean correct = perceived.equals(canonicalPhonemes);
        double score = scoringPolicy.singleWordScore(analyze);

        LlmContext context = promptBuilder.buildRetryContext(
                word,
                perceived,
                canonicalPhonemes,
                analyze.errors(),
                feedback.getWeakPhoneme());
        String guidance = safeRetryGuidance(context);

        return new RetryWordResult(correct, perceived, canonicalPhonemes, score, guidance);
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
        return FeedbackDetailResponse.from(feedback);
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

    // 평균 점수, 가장 빈도 높은 약점 음소, LLM 컨텍스트를 한 묶음으로 만든다.
    private Aggregated aggregate(List<Recording> recordings) {
        double accuracy = scoringPolicy.aggregate(recordings);
        List<AnalyzeError> aggregatedErrors = List.of();
        String weak = weakPhonemeAnalyzer.topOneFromErrors(aggregatedErrors);
        String targetText = firstNonBlank(recordings);
        LlmContext context = promptBuilder.buildAggregateContext(
                targetText, recordings, aggregatedErrors, weak);
        return new Aggregated(accuracy, weak, context);
    }

    private static String firstNonBlank(List<Recording> recordings) {
        for (Recording r : recordings) {
            String t = r.getTargetTextSnapshot();
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        return "";
    }

    private String safeSummarizeFeedback(LlmContext context) {
        try {
            String s = llmClient.summarizeFeedback(context);
            if (s != null && !s.isBlank()) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // 호출 실패 시에도 사용자 경험을 위해 폴백 문구를 돌려준다.
        }
        return feedbackFallback;
    }

    private String safeRetryGuidance(LlmContext context) {
        try {
            String s = llmClient.retryGuidance(context);
            if (s != null && !s.isBlank()) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // 같은 의미로 단어 재시도용 폴백.
        }
        return retryFallback;
    }

    private record Aggregated(double accuracy, String weakPhoneme, LlmContext context) {}
}

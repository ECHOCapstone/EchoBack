package com.capstoneecho.echo_back.pronunciation.feedback.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.member.service.MemberService;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackDetailResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackGenerateRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeOp;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PracticeWordResolver;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PronunciationPromptBuilder;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.feedback.support.WeakPhonemeAnalyzer;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FeedbackService {

    static final int DEFAULT_COMPLETION_EXP = 10;

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
            MemberService memberService) {
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> recordingIds = request.recordingIds();

        PronunciationFeedback feedback;
        List<Recording> recordings;
        if (hasScript) {
            Script script = scriptRepository.findById(request.scriptId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
            recordings = recordingRepository
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
            recordings = recordingRepository
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

        attachAggregatedErrors(feedback, recordings);
        PronunciationFeedback saved = feedbackRepository.save(feedback);
        return FeedbackDetailResponse.from(saved);
    }

    public FeedbackDetailResponse retryWord(
            Long userId, Long feedbackId, byte[] audioBytes, Integer wordIndex) {
        validateAudio(audioBytes);
        PronunciationFeedback feedback = feedbackRepository.findByIdAndUser_Id(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));

        String word = feedback.getPracticeWord();
        if (word == null || word.isBlank()) {
            word = PracticeWordResolver.DEFAULT_PRACTICE_WORD;
        }

        G2pResult g2p = modelServerClient.g2p(word);
        String canonical = g2p == null || g2p.phonemes() == null ? "" : g2p.phonemes();
        AnalyzeResult analyze = modelServerClient.analyze(audioBytes, canonical);

        LlmContext context = promptBuilder.buildRetryContext(
                word,
                analyze.perceived(),
                analyze.canonical().orElse(List.of()),
                analyze.errors(),
                feedback.getWeakPhoneme());
        String guidance = safeRetryGuidance(context);
        feedback.updateGuidance(guidance);
        return FeedbackDetailResponse.from(feedback);
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

    public FeedbackDetailResponse complete(Long userId, Long feedbackId) {
        Instant now = Instant.now();
        int affected = feedbackRepository.markCompletedAtomically(feedbackId, userId, now);
        if (affected == 1) {
            memberService.awardCompletionRewards(userId, DEFAULT_COMPLETION_EXP);
            PronunciationFeedback feedback = feedbackRepository
                    .findByIdAndUser_Id(feedbackId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
            return FeedbackDetailResponse.from(feedback);
        }
        PronunciationFeedback existing = feedbackRepository
                .findByIdAndUser_Id(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        if (!existing.isCompleted()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return FeedbackDetailResponse.from(existing);
    }

    private void requireFullMatch(List<Recording> found, List<Long> requested) {
        if (found.size() != requested.size()) {
            throw new BusinessException(ErrorCode.RECORDING_NOT_FOUND);
        }
    }

    private Aggregated aggregate(List<Recording> recordings) {
        double accuracy = scoringPolicy.aggregate(recordings);
        List<AnalyzeError> aggregatedErrors = new ArrayList<>();
        for (Recording r : recordings) {
            aggregatedErrors.addAll(parseErrors(r));
        }
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

    private void attachAggregatedErrors(
            PronunciationFeedback feedback, List<Recording> recordings) {
        for (Recording r : recordings) {
            for (AnalyzeError e : parseErrors(r)) {
                PhonemeOp op = mapOp(e.op());
                if (op == null) {
                    continue;
                }
                feedback.recordPhonemeError(op, e.canonical(), e.perceived(), e.canonicalIndex());
            }
        }
    }

    private static List<AnalyzeError> parseErrors(Recording r) {
        // 025 시점에는 Recording 에 errors_json 을 채우지 않으므로 빈 리스트로 폴백.
        return List.of();
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

    private String safeSummarizeFeedback(LlmContext context) {
        try {
            String s = llmClient.summarizeFeedback(context);
            if (s != null && !s.isBlank()) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "꾸준한 연습이 발음 개선에 도움이 됩니다.";
    }

    private String safeRetryGuidance(LlmContext context) {
        try {
            String s = llmClient.retryGuidance(context);
            if (s != null && !s.isBlank()) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "해당 단어를 한 번 더 천천히 따라 읽어 보세요.";
    }

    private void validateAudio(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < 12) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        if (audioBytes[0] != 'R'
                || audioBytes[1] != 'I'
                || audioBytes[2] != 'F'
                || audioBytes[3] != 'F') {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
    }

    private record Aggregated(double accuracy, String weakPhoneme, LlmContext context) {}
}

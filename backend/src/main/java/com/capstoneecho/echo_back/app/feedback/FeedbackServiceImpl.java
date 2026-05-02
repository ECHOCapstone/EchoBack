package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackResponse;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.app.feedback.dto.GenerateFeedbackRequest;
import com.capstoneecho.echo_back.app.feedback.dto.ModelAnalyzeResponse;
import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.feedback.dto.RetryWordResponse;
import com.capstoneecho.echo_back.app.member.MemberService;
import com.capstoneecho.echo_back.app.member.dto.UserResponse;
import com.capstoneecho.echo_back.app.recording.Recording;
import com.capstoneecho.echo_back.app.recording.RecordingRepository;
import com.capstoneecho.echo_back.app.script.Script;
import com.capstoneecho.echo_back.app.script.ScriptService;
import com.capstoneecho.echo_back.app.session.SessionService;
import tools.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 한 챕터 / 세션을 끝낸 뒤 종합 피드백을 만들고, 그 피드백의 권장 단어를 다시 발음했을 때
// 평가까지 처리한다. recording 들의 평균 점수를 정확도로 잡고, 가장 자주 틀린 음소를 약점으로
// 추출한 뒤 LlmFeedbackGenerator 가 한국어 안내문을 만든다.
@Service
@Transactional
class FeedbackServiceImpl implements FeedbackService {

    private static final TypeReference<List<ModelAnalyzeResponse.AlignmentItem>> ALIGNMENT_LIST =
            new TypeReference<>() {};

    private final FeedbackRepository feedbackRepository;
    private final RecordingRepository recordingRepository;
    private final ScriptService scriptService;
    private final SessionService sessionService;
    private final ScoringPolicy scoringPolicy;
    private final LlmFeedbackGenerator llmGenerator;
    private final PracticeWordResolver practiceWordResolver;
    private final ModelServerClient modelClient;
    private final MemberService memberService;
    private final ObjectMapper objectMapper;
    private final int completionExpReward;

    FeedbackServiceImpl(
            FeedbackRepository feedbackRepository,
            RecordingRepository recordingRepository,
            ScriptService scriptService,
            SessionService sessionService,
            ScoringPolicy scoringPolicy,
            LlmFeedbackGenerator llmGenerator,
            PracticeWordResolver practiceWordResolver,
            ModelServerClient modelClient,
            MemberService memberService,
            ObjectMapper objectMapper,
            AppProperties properties
    ) {
        this.feedbackRepository = feedbackRepository;
        this.recordingRepository = recordingRepository;
        this.scriptService = scriptService;
        this.sessionService = sessionService;
        this.scoringPolicy = scoringPolicy;
        this.llmGenerator = llmGenerator;
        this.practiceWordResolver = practiceWordResolver;
        this.modelClient = modelClient;
        this.memberService = memberService;
        this.objectMapper = objectMapper;
        this.completionExpReward = properties.reward().completionExp();
    }

    @Override
    public FeedbackResponse generate(Long userId, GenerateFeedbackRequest request) {
        validateTarget(request);

        var script = request.scriptId() != null ? scriptService.getEntity(request.scriptId()) : null;
        var title = resolveTitle(userId, request, script);
        var recordings = recordingRepository.findByUserIdAndIdIn(userId, request.recordingIds());
        if (recordings.size() != request.recordingIds().size()) {
            throw new BusinessException(ErrorCode.RECORDING_NOT_FOUND);
        }

        double accuracy = scoringPolicy.averageScore(stepScoresOf(recordings));
        var aggregatedErrors = aggregateErrors(recordings);
        var weakPhoneme = pickWeakPhoneme(aggregatedErrors);
        var guidance = llmGenerator.unitGuidance(title, accuracy, weakPhoneme, aggregatedErrors);
        var practiceWord = practiceWordResolver.resolve(script, weakPhoneme);

        var feedback = PronunciationFeedback.create(
                userId,
                request.scriptId(),
                request.sessionId(),
                title,
                accuracy,
                weakPhoneme,
                practiceWord,
                guidance
        );
        for (var err : aggregatedErrors) {
            feedback.addError(PhonemeError.of(err.op(), err.canonical(), err.perceived(), err.canonicalIndex()));
        }
        return FeedbackResponse.from(feedbackRepository.save(feedback));
    }

    @Override
    public RetryWordResponse retryWord(Long userId, Long feedbackId, MultipartFile audio) {
        var feedback = feedbackRepository.findByIdAndUserId(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "audio 파일이 비어 있습니다.");
        }
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED, e.getMessage());
        }

        var practiceWord = feedback.getPracticeWord();
        var canonical = practiceWord != null ? practiceWord.toLowerCase() : null;
        var analysis = modelClient.analyze(
                bytes,
                audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.wav",
                audio.getContentType() != null ? audio.getContentType() : "application/octet-stream",
                null
        );

        var correct = isPracticeWordCorrect(analysis.perceived(), canonical);
        var score = scoringPolicy.scoreOf(analysis);
        var guidance = llmGenerator.retryGuidance(practiceWord, correct, analysis.perceived(), analysis.canonical());

        return new RetryWordResponse(
                correct,
                analysis.perceived(),
                analysis.canonical(),
                score,
                guidance
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackSummaryResponse> listMine(Long userId) {
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FeedbackSummaryResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackResponse get(Long userId, Long feedbackId) {
        var feedback = feedbackRepository.findByIdAndUserId(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        return FeedbackResponse.from(feedback);
    }

    @Override
    public UserResponse complete(Long userId, Long feedbackId) {
        // 같은 피드백을 두 번 complete 해도 EXP 가 두 번 더해지지 않도록 markCompleted 로 막는다.
        var feedback = feedbackRepository.findByIdAndUserId(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        var user = feedback.markCompleted()
                ? memberService.awardCompletionRewards(userId, completionExpReward)
                : memberService.getById(userId);
        return UserResponse.from(user);
    }

    private void validateTarget(GenerateFeedbackRequest request) {
        var hasScript = request.scriptId() != null;
        var hasSession = request.sessionId() != null;
        if (hasScript == hasSession) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "scriptId 또는 sessionId 중 정확히 하나만 지정해야 합니다.");
        }
    }

    private String resolveTitle(Long userId, GenerateFeedbackRequest request, Script script) {
        if (script != null) {
            return script.getTitle();
        }
        return sessionService.getEntity(userId, request.sessionId()).getTitle();
    }

    private List<Double> stepScoresOf(List<Recording> recordings) {
        var scores = new ArrayList<Double>(recordings.size());
        for (var r : recordings) {
            if (r.getStepScore() != null) {
                scores.add(r.getStepScore());
            }
        }
        return scores;
    }

    private List<PhonemeErrorResponse> aggregateErrors(List<Recording> recordings) {
        var aggregated = new ArrayList<PhonemeErrorResponse>();
        for (var r : recordings) {
            var raw = r.getErrorsJson();
            if (raw == null || raw.isBlank()) continue;
            try {
                List<ModelAnalyzeResponse.AlignmentItem> items = objectMapper.readValue(raw, ALIGNMENT_LIST);
                for (var item : items) {
                    aggregated.add(new PhonemeErrorResponse(
                            item.op(), item.canonical(), item.recognized(), item.canonicalIndex()));
                }
            } catch (Exception ignored) {
                // 손상된 JSON 은 무시한다.
            }
        }
        return aggregated;
    }

    private String pickWeakPhoneme(List<PhonemeErrorResponse> errors) {
        if (errors.isEmpty()) return null;
        Map<String, Integer> count = new HashMap<>();
        for (var e : errors) {
            var key = e.canonical() != null ? e.canonical() : e.perceived();
            if (key == null) continue;
            count.merge(key, 1, Integer::sum);
        }
        return count.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private boolean isPracticeWordCorrect(List<String> perceived, String practiceWord) {
        if (perceived == null || perceived.isEmpty() || practiceWord == null) return false;
        // 단순 휴리스틱: perceived 음소가 단어의 첫 글자 발음을 포함하는지 확인.
        var first = String.valueOf(practiceWord.charAt(0)).toLowerCase();
        for (var p : perceived) {
            if (p != null && p.toLowerCase().startsWith(first)) {
                return true;
            }
        }
        return false;
    }
}

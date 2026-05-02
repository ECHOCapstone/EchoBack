package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackResponse;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.app.feedback.dto.GenerateFeedbackRequest;
import com.capstoneecho.echo_back.app.feedback.dto.RetryWordResponse;
import com.capstoneecho.echo_back.app.member.MemberService;
import com.capstoneecho.echo_back.app.member.dto.UserResponse;
import com.capstoneecho.echo_back.app.recording.Recording;
import com.capstoneecho.echo_back.app.recording.RecordingRepository;
import com.capstoneecho.echo_back.app.script.Script;
import com.capstoneecho.echo_back.app.script.ScriptService;
import com.capstoneecho.echo_back.app.session.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// 한 회차 학습이 끝났을 때의 종합 피드백 생성과, 권장 단어 재발음 평가를 담당한다.
// 흐름 조립만 여기에 두고, 음소 빈도 집계 / JSON 변환 / 점수 계산 / 한국어 가이드 작성은
// 각각의 컴포넌트에 위임한다.
@Service
@Transactional
class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final RecordingRepository recordingRepository;
    private final ScriptService scriptService;
    private final SessionService sessionService;
    private final ScoringPolicy scoringPolicy;
    private final LlmFeedbackGenerator llmGenerator;
    private final PracticeWordResolver practiceWordResolver;
    private final WeakPhonemeAnalyzer weakPhonemeAnalyzer;
    private final ModelServerClient modelClient;
    private final MemberService memberService;
    private final int completionExpReward;

    FeedbackServiceImpl(
            FeedbackRepository feedbackRepository,
            RecordingRepository recordingRepository,
            ScriptService scriptService,
            SessionService sessionService,
            ScoringPolicy scoringPolicy,
            LlmFeedbackGenerator llmGenerator,
            PracticeWordResolver practiceWordResolver,
            WeakPhonemeAnalyzer weakPhonemeAnalyzer,
            ModelServerClient modelClient,
            MemberService memberService,
            AppProperties properties
    ) {
        this.feedbackRepository = feedbackRepository;
        this.recordingRepository = recordingRepository;
        this.scriptService = scriptService;
        this.sessionService = sessionService;
        this.scoringPolicy = scoringPolicy;
        this.llmGenerator = llmGenerator;
        this.practiceWordResolver = practiceWordResolver;
        this.weakPhonemeAnalyzer = weakPhonemeAnalyzer;
        this.modelClient = modelClient;
        this.memberService = memberService;
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
        var weakness = weakPhonemeAnalyzer.analyze(recordings);
        var guidance = llmGenerator.unitGuidance(title, accuracy, weakness.weakPhoneme(), weakness.errors());
        var practiceWord = practiceWordResolver.resolve(script, weakness.weakPhoneme());

        var feedback = PronunciationFeedback.create(
                userId,
                request.scriptId(),
                request.sessionId(),
                title,
                accuracy,
                weakness.weakPhoneme(),
                practiceWord,
                guidance
        );
        for (var err : weakness.errors()) {
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
        var analysis = modelClient.analyze(
                bytes,
                audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.wav",
                audio.getContentType() != null ? audio.getContentType() : "application/octet-stream",
                null
        );

        var evaluation = llmGenerator.evaluateRetry(practiceWord, analysis.perceived(), analysis.canonical());
        var score = scoringPolicy.scoreOf(analysis);

        return new RetryWordResponse(
                evaluation.correct(),
                analysis.perceived(),
                analysis.canonical(),
                score,
                evaluation.guidance()
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

}

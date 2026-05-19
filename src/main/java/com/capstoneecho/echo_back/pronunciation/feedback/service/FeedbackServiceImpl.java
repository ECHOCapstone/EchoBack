package com.capstoneecho.echo_back.pronunciation.feedback.service;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackDetailResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackGenerateRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.RetryWordResult;
import com.capstoneecho.echo_back.member.service.MemberService;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.pronunciation.recording.support.MultipartAudioReader;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import com.capstoneecho.echo_back.external.llm.LlmFeedbackGenerator;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.learning.script.service.ScriptService;
import com.capstoneecho.echo_back.learning.session.service.SessionService;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeOp;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PracticeWordResolver;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.feedback.support.WeakPhonemeAnalyzer;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
// 한 회차 학습이 끝났을 때의 종합 피드백 생성과, 권장 단어 재발음 평가를 담당한다.
// 흐름 조립만 여기에 두고, 음소 빈도 집계 / JSON 변환 / 점수 계산 / 한국어 가이드 작성은
// 각각의 컴포넌트에 위임한다.
@Service
@Transactional
public class FeedbackServiceImpl implements FeedbackService {

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
    private final MultipartAudioReader audioReader;
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
            MultipartAudioReader audioReader,
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
        this.audioReader = audioReader;
        this.completionExpReward = properties.reward().completionExp();
    }

    @Override
    public FeedbackDetailResponse generate(Long userId, FeedbackGenerateRequest request) {
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
        var practiceWord = practiceWordResolver.resolve(script, weakness.weakPhoneme(), title);

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
            feedback.recordPhonemeError(PhonemeOp.valueOf(err.op()), err.canonical(), err.perceived(), err.canonicalIndex());
        }
        return FeedbackDetailResponse.from(feedbackRepository.save(feedback));
    }

    @Override
    public RetryWordResult retryWord(Long userId, Long feedbackId, MultipartFile audio) {
        var feedback = feedbackRepository.findByIdAndUserId(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        var bytes = audioReader.read(audio);

        var practiceWord = feedback.getPracticeWord();
        // 재연습 단어가 있으면 G2P 로 정답 음소를 만들어 모델 서버에 같이 보낸다.
        // 정렬·오류·PER 까지 받아두면 LLM 평가가 빈 입력 같은 케이스에서 환각하는 것을 막을 수 있다.
        var canonical = (practiceWord != null && !practiceWord.isBlank())
                ? modelClient.g2p(practiceWord)
                : null;
        // 파일명/콘텐츠 타입 기본값은 ModelServerClient 가 단독으로 채운다 (SSOT).
        var analysis = modelClient.analyze(bytes, audio.getOriginalFilename(), audio.getContentType(), canonical);

        var evaluation = llmGenerator.evaluateRetry(practiceWord, analysis.perceived(), analysis.canonical());
        var score = scoringPolicy.scoreOf(analysis);

        return new RetryWordResult(
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
    public FeedbackDetailResponse get(Long userId, Long feedbackId) {
        var feedback = feedbackRepository.findByIdAndUserId(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        return FeedbackDetailResponse.from(feedback);
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

    private void validateTarget(FeedbackGenerateRequest request) {
        var hasScript = request.scriptId() != null;
        var hasSession = request.sessionId() != null;
        if (hasScript == hasSession) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "scriptId 또는 sessionId 중 정확히 하나만 지정해야 합니다.");
        }
    }

    private String resolveTitle(Long userId, FeedbackGenerateRequest request, Script script) {
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

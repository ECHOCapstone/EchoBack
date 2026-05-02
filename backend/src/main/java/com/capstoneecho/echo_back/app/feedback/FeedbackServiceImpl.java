package com.capstoneecho.echo_back.app.feedback;

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

// 학습 unit 종합 피드백 + 재연습 단어 평가의 단일 진입점.
//
// generate 흐름
//   1. 요청 검증 (scriptId XOR sessionId, recordingIds 소유 확인)
//   2. recording 의 stepScore 평균 → unit 정확도
//   3. 각 recording 의 errorsJson 을 모아 가장 빈번한 canonical 음소 → weakPhoneme
//   4. LlmFeedbackGenerator 로 한국어 가이드 문장 생성
//   5. PracticeWordResolver 로 재연습 단어 결정 (Script.practiceWord → 음소매핑 → 제목 → default)
//   6. PronunciationFeedback + PhonemeError 영속화
//
// retryWord 흐름
//   1. feedbackId 소유 확인 + practiceWord 가져오기
//   2. 모델 서버 /analyze 직접 호출 (canonical 미전송 — 단어 1개의 정답을 알 수는 없으니
//      클라이언트가 재연습 단어 발음 결과만 보고 정/오를 판단)
//   3. perceived 와 단어 자체의 단순 매칭으로 correct 판정 + LLM 가이드 생성
@Service
@Transactional
class FeedbackServiceImpl implements FeedbackService {

    private static final TypeReference<List<ModelAnalyzeResponse.AlignmentItem>> ALIGNMENT_LIST =
            new TypeReference<>() {};

    // 챕터 학습 완료 시 사용자에게 지급되는 기본 EXP. 추후 정확도/난이도별 차등을 줄 수 있다.
    private static final int COMPLETION_EXP_REWARD = 1000;

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
            ObjectMapper objectMapper
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
        var practiceWord = practiceWordResolver.resolve(script, weakPhoneme, title);

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
        // feedback 소유 확인만 검증으로 사용. 보상 지급 자체는 MemberService 에 위임해 SRP 를 지킨다.
        feedbackRepository.findByIdAndUserId(feedbackId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        var user = memberService.awardCompletionRewards(userId, COMPLETION_EXP_REWARD);
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

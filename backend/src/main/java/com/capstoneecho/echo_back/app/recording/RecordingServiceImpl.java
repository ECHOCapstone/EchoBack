package com.capstoneecho.echo_back.app.recording;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.feedback.LlmFeedbackGenerator;
import com.capstoneecho.echo_back.app.feedback.ModelServerClient;
import com.capstoneecho.echo_back.app.feedback.ScoringPolicy;
import com.capstoneecho.echo_back.app.feedback.dto.ModelAnalyzeResponse;
import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.recording.dto.RecordingResponse;
import com.capstoneecho.echo_back.app.script.ScriptService;
import com.capstoneecho.echo_back.app.session.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

// 녹음 1건 업로드 흐름: 검증 → 디스크 저장 → 모델 서버 분석 → 점수 산출 → DB 영속화.
// scriptId 와 sessionId 는 정확히 하나만 지정되어야 한다.
@Service
@Transactional
class RecordingServiceImpl implements RecordingService {

    private final RecordingRepository repository;
    private final RecordingStorage storage;
    private final ModelServerClient modelClient;
    private final ScoringPolicy scoringPolicy;
    private final ScriptService scriptService;
    private final SessionService sessionService;
    private final LlmFeedbackGenerator llmGenerator;
    private final ObjectMapper objectMapper;

    RecordingServiceImpl(
            RecordingRepository repository,
            RecordingStorage storage,
            ModelServerClient modelClient,
            ScoringPolicy scoringPolicy,
            ScriptService scriptService,
            SessionService sessionService,
            LlmFeedbackGenerator llmGenerator,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.storage = storage;
        this.modelClient = modelClient;
        this.scoringPolicy = scoringPolicy;
        this.scriptService = scriptService;
        this.sessionService = sessionService;
        this.llmGenerator = llmGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecordingResponse upload(
            Long userId,
            Long scriptId,
            Long sessionId,
            Long stepId,
            Long sessionSentenceId,
            MultipartFile audio
    ) {
        validateTarget(scriptId, sessionId);
        validateAudio(audio);

        var target = resolveTarget(userId, scriptId, sessionId, stepId, sessionSentenceId);

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED, e.getMessage());
        }

        var stored = storage.save(userId, audio.getOriginalFilename(), bytes);
        // 호출 시점에 결정된 학습 종류에 따라 도메인 의도 그대로 저장한다 (SRP).
        // forScriptStep 추천 학습 / forSessionSentence 한 문장 단위 / forSessionFreeForm 통째 녹음.
        var entity = repository.save(buildRecording(userId, scriptId, sessionId, stepId, sessionSentenceId, stored.path()));

        var analysis = modelClient.analyze(
                bytes,
                stored.filename() != null ? stored.filename() : "audio.wav",
                resolveContentType(audio),
                target.canonical()
        );
        var score = scoringPolicy.scoreOf(analysis);
        var errorList = toErrorResponses(analysis.errors());
        var guidanceKr = llmGenerator.stepGuidance(
                target.targetText(),
                score,
                analysis.perceived(),
                analysis.canonical(),
                errorList
        );
        entity.applyAnalysis(
                joinStrings(analysis.perceived()),
                joinStrings(analysis.canonical()),
                joinDoubles(analysis.peakSoftmax()),
                serializeErrors(analysis.errors()),
                guidanceKr,
                analysis.durationSec(),
                score
        );
        return RecordingResponse.from(entity);
    }

    // 모델 호출과 LLM 가이드 생성에 필요한 두 입력값을 한 곳에서 결정한다.
    //   canonical    : 모델 서버 /analyze 에 보낼 정답 음소 시퀀스 (없으면 PER 미산출)
    //   targetText   : 한국어 가이드 문장에 넣을 사용자 발음 목표 텍스트
    //
    // 분기 규칙
    //   - scriptId 있음 + stepId 있음  : 추천 학습의 한 step (canonical/targetText 모두 결정됨)
    //   - scriptId 있음 + stepId 없음  : 추천 학습 자유 녹음 (캐넌 없이 전체 스크립트 평가)
    //   - sessionSentenceId 있음       : 맞춤 학습 한 문장 단위 (sentence.text 가 targetText)
    //   - sessionId 만 있음            : 맞춤 학습 통째 녹음 (session.scriptText 가 targetText)
    private TargetResolution resolveTarget(
            Long userId,
            Long scriptId,
            Long sessionId,
            Long stepId,
            Long sessionSentenceId
    ) {
        if (scriptId != null) {
            scriptService.getEntity(scriptId);
            if (stepId != null) {
                LearningStep step = scriptService.getStep(scriptId, stepId);
                return new TargetResolution(step.getCanonicalPhonemes(), step.getTargetText());
            }
            return new TargetResolution(null, null);
        }
        if (sessionSentenceId != null) {
            var sentence = sessionService.getSentence(userId, sessionSentenceId);
            return new TargetResolution(null, sentence.getText());
        }
        var session = sessionService.getEntity(userId, sessionId);
        return new TargetResolution(null, session.getScriptText());
    }

    // 모델 호출용 canonical 과 가이드용 targetText 를 한 묶음으로 들고 다니기 위한 값 객체.
    private record TargetResolution(String canonical, String targetText) {}

    private List<PhonemeErrorResponse> toErrorResponses(List<ModelAnalyzeResponse.AlignmentItem> errors) {
        if (errors == null || errors.isEmpty()) return List.of();
        return errors.stream()
                .map(e -> new PhonemeErrorResponse(e.op(), e.canonical(), e.recognized(), e.canonicalIndex()))
                .toList();
    }

    private String serializeErrors(List<ModelAnalyzeResponse.AlignmentItem> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Recording getEntity(Long userId, Long recordingId) {
        return repository.findByIdAndUserId(recordingId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
    }

    private void validateTarget(Long scriptId, Long sessionId) {
        var scriptPresent = scriptId != null;
        var sessionPresent = sessionId != null;
        if (scriptPresent == sessionPresent) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "scriptId 또는 sessionId 중 하나만 지정해야 합니다.");
        }
    }

    // validateTarget 을 통과한 입력에 한해 학습 종류별 정적 팩토리로 분기한다.
    private static Recording buildRecording(
            Long userId,
            Long scriptId,
            Long sessionId,
            Long stepId,
            Long sessionSentenceId,
            String audioPath
    ) {
        if (scriptId != null) {
            return Recording.forScriptStep(userId, scriptId, stepId, audioPath);
        }
        if (sessionSentenceId != null) {
            return Recording.forSessionSentence(userId, sessionId, sessionSentenceId, audioPath);
        }
        return Recording.forSessionFreeForm(userId, sessionId, audioPath);
    }

    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "audio 파일이 비어 있습니다.");
        }
    }

    private String resolveContentType(MultipartFile audio) {
        var ct = audio.getContentType();
        return ct != null ? ct : "application/octet-stream";
    }

    private static String joinStrings(List<String> values) {
        return values == null ? null : String.join(" ", values);
    }

    private static String joinDoubles(List<Double> values) {
        if (values == null) return null;
        var sb = new StringBuilder();
        for (var v : values) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(v);
        }
        return sb.toString();
    }
}

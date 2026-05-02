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
            MultipartFile audio
    ) {
        validateTarget(scriptId, sessionId);
        validateAudio(audio);

        // 추천 학습 흐름이면 step 의 canonical 음소와 targetText 를 사용해 채팅 가이드를 만든다.
        // 맞춤 학습(session) 은 사용자가 입력한 scriptText 가 그 자리의 targetText 가 된다.
        String canonical = null;
        String targetText = null;
        if (scriptId != null) {
            scriptService.getEntity(scriptId);
            if (stepId != null) {
                LearningStep step = scriptService.getStep(scriptId, stepId);
                canonical = step.getCanonicalPhonemes();
                targetText = step.getTargetText();
            }
        } else {
            var session = sessionService.getEntity(userId, sessionId);
            targetText = session.getScriptText();
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED, e.getMessage());
        }

        var stored = storage.save(userId, audio.getOriginalFilename(), bytes);
        var entity = repository.save(Recording.create(userId, scriptId, sessionId, stepId, stored.path()));

        var analysis = modelClient.analyze(
                bytes,
                stored.filename() != null ? stored.filename() : "audio.wav",
                resolveContentType(audio),
                canonical
        );
        var score = scoringPolicy.scoreOf(analysis);
        var errorList = toErrorResponses(analysis.errors());
        var guidanceKr = llmGenerator.stepGuidance(
                targetText,
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

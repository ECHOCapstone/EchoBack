package com.capstoneecho.echo_back.app.recording;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.feedback.LlmFeedbackGenerator;
import com.capstoneecho.echo_back.app.feedback.ModelServerClient;
import com.capstoneecho.echo_back.app.feedback.PhonemeErrorMapper;
import com.capstoneecho.echo_back.app.feedback.ScoringPolicy;
import com.capstoneecho.echo_back.app.recording.dto.RecordingResponse;
import com.capstoneecho.echo_back.app.script.ScriptService;
import com.capstoneecho.echo_back.app.session.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final PhonemeErrorMapper errorMapper;

    RecordingServiceImpl(
            RecordingRepository repository,
            RecordingStorage storage,
            ModelServerClient modelClient,
            ScoringPolicy scoringPolicy,
            ScriptService scriptService,
            SessionService sessionService,
            LlmFeedbackGenerator llmGenerator,
            PhonemeErrorMapper errorMapper
    ) {
        this.repository = repository;
        this.storage = storage;
        this.modelClient = modelClient;
        this.scoringPolicy = scoringPolicy;
        this.scriptService = scriptService;
        this.sessionService = sessionService;
        this.llmGenerator = llmGenerator;
        this.errorMapper = errorMapper;
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

        var targetText = resolveTargetText(userId, scriptId, sessionId, stepId, sessionSentenceId);

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED, e.getMessage());
        }

        var stored = storage.save(userId, audio.getOriginalFilename(), bytes);
        // 학습 종류에 맞는 팩토리로 Recording 을 만든다 (forScriptStep / forSessionSentence / forSessionFreeForm).
        var entity = repository.save(buildRecording(userId, scriptId, sessionId, stepId, sessionSentenceId, stored.path()));

        // 정답 음소는 도메인이 보관하지 않고, targetText 가 있을 때 모델 서버 G2P 로 즉석 산출한다.
        // targetText 가 없는 자유 발화는 null 로 남겨 정렬·오류 비교 자체를 생략한다.
        var canonical = canonicalFor(targetText);

        var analysis = modelClient.analyze(
                bytes,
                stored.filename() != null ? stored.filename() : "audio.wav",
                resolveContentType(audio),
                canonical
        );
        var score = scoringPolicy.scoreOf(analysis);
        var errorList = errorMapper.toResponses(analysis.errors());
        var step = llmGenerator.stepGuidance(
                targetText,
                score,
                analysis.perceived(),
                analysis.canonical(),
                errorList
        );
        entity.applyAnalysis(new Recording.AnalysisOutcome(
                joinStrings(analysis.perceived()),
                joinStrings(analysis.canonical()),
                joinDoubles(analysis.peakSoftmax()),
                errorMapper.serialize(analysis.errors()),
                step.message(),
                analysis.durationSec(),
                score
        ));
        return RecordingResponse.from(entity, errorList, step.wrongWords());
    }

    // 학습 종류에 따라 사용자가 발음할 영문 원문을 골라낸다.
    //   - script + step : 추천 학습의 한 step
    //   - script 만     : 추천 학습 자유 녹음 (원문 없음)
    //   - sentenceId    : 맞춤 학습의 한 문장
    //   - session 만    : 맞춤 학습을 통째로
    private String resolveTargetText(
            Long userId,
            Long scriptId,
            Long sessionId,
            Long stepId,
            Long sessionSentenceId
    ) {
        if (scriptId != null) {
            scriptService.getEntity(scriptId);
            if (stepId != null) {
                return scriptService.getStep(scriptId, stepId).getTargetText();
            }
            return null;
        }
        if (sessionSentenceId != null) {
            return sessionService.getSentence(userId, sessionSentenceId).getText();
        }
        return sessionService.getEntity(userId, sessionId).getScriptText();
    }

    // 텍스트가 없으면 null, 있으면 모델 서버 G2P 결과를 그대로 사용한다.
    private String canonicalFor(String targetText) {
        if (targetText == null || targetText.isBlank()) {
            return null;
        }
        return modelClient.g2p(targetText);
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

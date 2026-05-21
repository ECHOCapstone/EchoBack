package com.capstoneecho.echo_back.pronunciation.recording.dto;

import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.PhonemeTip;
import com.capstoneecho.echo_back.external.llm.WrongWord;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// 녹음 1건 업로드 후 클라이언트에 돌려주는 응답.
// 모델 서버 분석 결과 (perceived / canonical / errors / stepScore) 와
// LLM 구조화 결과 (guidanceKr / passed / retryRecommended / wrongWords / phonemeTips ...) 를 함께 담는다.
// passed 는 step 통과 임계 (app.gamification.pass-threshold) 기준 — 클라이언트의 "다음으로 / 재학습" 분기 SSOT.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordingUploadResponse(
        Long id,
        Long scriptId,
        Long sessionId,
        Long stepId,
        Long sessionSentenceId,
        Double durationSec,
        List<String> perceived,
        List<String> canonical,
        List<Double> peakSoftmax,
        Double stepScore,
        boolean passed,
        boolean retryRecommended,
        String guidanceKr,
        List<String> strengths,
        List<String> weaknesses,
        List<PhonemeErrorView> errors,
        List<WrongWord> wrongWords,
        List<PhonemeTip> phonemeTips,
        // 모델 서버가 분류한 발화 속도. FAST 면 프론트가 "조금 천천히" 안내를 노출한다.
        SpeechRate speechRate,
        Instant createdAt
) {

    private static final Logger log = LoggerFactory.getLogger(RecordingUploadResponse.class);
    private static final TypeReference<List<WrongWord>> WRONG_WORDS_TYPE = new TypeReference<>() {};

    public record PhonemeErrorView(
            String op,
            String canonical,
            String perceived,
            Integer canonicalIndex
    ) {}

    // 저장된 Recording 으로부터 read-side 응답을 재구성한다.
    // wrongWordsJson 은 List<WrongWord> 로 역직렬화하며, 파싱 실패 / NULL 입력은 빈 리스트로 폴백한다.
    // 음소 시퀀스는 엔티티에 정규화 저장되지 않으므로 빈 리스트로 채워진다.
    public static RecordingUploadResponse from(Recording recording, ObjectMapper objectMapper) {
        if (recording == null) {
            throw new IllegalArgumentException("recording is required");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper is required");
        }
        Long scriptId = recording.getScript() == null ? null : recording.getScript().getId();
        Long stepId = recording.getStep() == null ? null : recording.getStep().getId();
        Long sessionId = recording.getSession() == null ? null : recording.getSession().getId();
        Long sentenceId = recording.getSessionSentence() == null
                ? null
                : recording.getSessionSentence().getId();
        return new RecordingUploadResponse(
                recording.getId(),
                scriptId,
                sessionId,
                stepId,
                sentenceId,
                recording.getDurationSec(),
                List.of(),
                List.of(),
                List.of(),
                recording.getStepScore(),
                false,
                false,
                recording.getGuidanceKr(),
                List.of(),
                List.of(),
                List.of(),
                deserializeWrongWords(recording.getId(), recording.getWrongWordsJson(), objectMapper),
                List.of(),
                // 조회 응답은 엔티티에 발화 속도가 저장되지 않아 NORMAL 로 폴백.
                SpeechRate.NORMAL,
                recording.getCreatedAt());
    }

    // step 업로드 직후 LLM 결과 + 모델 서버 결과를 합쳐 응답을 만든다.
    public static RecordingUploadResponse fromUpload(
            Recording saved,
            List<String> perceived,
            List<String> canonical,
            List<Double> peakSoftmax,
            List<PhonemeErrorView> errors,
            LlmStepFeedback feedback,
            boolean passed,
            SpeechRate speechRate) {
        Long scriptId = saved.getScript() == null ? null : saved.getScript().getId();
        Long stepId = saved.getStep() == null ? null : saved.getStep().getId();
        Long sessionId = saved.getSession() == null ? null : saved.getSession().getId();
        Long sentenceId = saved.getSessionSentence() == null
                ? null
                : saved.getSessionSentence().getId();
        return new RecordingUploadResponse(
                saved.getId(),
                scriptId,
                sessionId,
                stepId,
                sentenceId,
                saved.getDurationSec(),
                perceived == null ? List.of() : List.copyOf(perceived),
                canonical == null ? List.of() : List.copyOf(canonical),
                peakSoftmax == null ? List.of() : List.copyOf(peakSoftmax),
                saved.getStepScore(),
                passed,
                feedback.retryRecommended(),
                feedback.guidanceKr(),
                feedback.strengths(),
                feedback.weaknesses(),
                errors == null ? List.of() : List.copyOf(errors),
                feedback.wrongWords(),
                feedback.phonemeTips(),
                speechRate == null ? SpeechRate.NORMAL : speechRate,
                saved.getCreatedAt());
    }

    private static List<WrongWord> deserializeWrongWords(
            Long recordingId, String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<WrongWord> parsed = objectMapper.readValue(json, WRONG_WORDS_TYPE);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to deserialize wrongWordsJson for recording id={}; falling back to []",
                    recordingId,
                    ex);
            return List.of();
        }
    }
}

package com.capstoneecho.echo_back.pronunciation.recording.dto;

import com.capstoneecho.echo_back.external.llm.WrongWord;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
        String guidanceKr,
        List<PhonemeErrorView> errors,
        List<WrongWord> wrongWords,
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

    // 저장된 Recording 으로부터 응답 DTO 를 재구성한다.
    // wrongWordsJson 은 List<WrongWord> 로 역직렬화하고, 파싱 실패 / NULL 입력은 빈 리스트로 폴백한다.
    // 엔티티에 정규화 저장되지 않는 음소 시퀀스 필드는 빈 리스트로 채워진다.
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
                recording.getGuidanceKr(),
                List.of(),
                deserializeWrongWords(recording.getId(), recording.getWrongWordsJson(), objectMapper),
                recording.getCreatedAt());
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

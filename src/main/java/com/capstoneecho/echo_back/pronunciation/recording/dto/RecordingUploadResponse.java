package com.capstoneecho.echo_back.pronunciation.recording.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

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
        List<String> wrongWords,
        Instant createdAt
) {

    public record PhonemeErrorView(
            String op,
            String canonical,
            String perceived,
            Integer canonicalIndex
    ) {}
}

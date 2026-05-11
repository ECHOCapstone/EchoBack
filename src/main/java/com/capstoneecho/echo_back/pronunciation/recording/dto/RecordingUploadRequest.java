package com.capstoneecho.echo_back.pronunciation.recording.dto;

public record RecordingUploadRequest(
        Long scriptId,
        Long stepId,
        Long sessionId,
        Long sessionSentenceId
) {
}

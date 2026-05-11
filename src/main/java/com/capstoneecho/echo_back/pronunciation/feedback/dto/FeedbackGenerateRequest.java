package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record FeedbackGenerateRequest(
        Long scriptId,
        Long sessionId,
        @NotEmpty List<Long> recordingIds
) {}

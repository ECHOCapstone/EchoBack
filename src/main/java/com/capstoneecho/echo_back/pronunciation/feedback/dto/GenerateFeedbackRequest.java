package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// 한 unit/세션 종료 후 누적된 녹음 ID 들로 종합 피드백을 요청한다.
// scriptId 또는 sessionId 중 정확히 하나만 지정한다.
public record GenerateFeedbackRequest(
        Long scriptId,
        Long sessionId,
        @NotEmpty List<Long> recordingIds
) {}

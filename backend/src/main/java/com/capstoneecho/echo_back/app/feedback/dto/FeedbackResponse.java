package com.capstoneecho.echo_back.app.feedback.dto;

import com.capstoneecho.echo_back.app.feedback.PronunciationFeedback;

import java.time.Instant;
import java.util.List;

public record FeedbackResponse(
        Long id,
        Long scriptId,
        Long sessionId,
        String title,
        double accuracy,
        String weakPhoneme,
        String practiceWord,
        String guidanceKr,
        List<PhonemeErrorResponse> errors,
        Instant createdAt
) {

    public static FeedbackResponse from(PronunciationFeedback f) {
        return new FeedbackResponse(
                f.getId(),
                f.getScriptId(),
                f.getSessionId(),
                f.getTitle(),
                f.getAccuracy(),
                f.getWeakPhoneme(),
                f.getPracticeWord(),
                f.getGuidanceKr(),
                f.getErrors().stream().map(PhonemeErrorResponse::from).toList(),
                f.getCreatedAt()
        );
    }
}

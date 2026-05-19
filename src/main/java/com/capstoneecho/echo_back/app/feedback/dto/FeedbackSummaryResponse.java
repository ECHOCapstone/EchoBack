package com.capstoneecho.echo_back.app.feedback.dto;

import com.capstoneecho.echo_back.app.feedback.PronunciationFeedback;

import java.time.Instant;

public record FeedbackSummaryResponse(
        Long id,
        String title,
        double accuracy,
        String weakPhoneme,
        Instant createdAt
) {

    public static FeedbackSummaryResponse from(PronunciationFeedback f) {
        return new FeedbackSummaryResponse(
                f.getId(),
                f.getTitle(),
                f.getAccuracy(),
                f.getWeakPhoneme(),
                f.getCreatedAt()
        );
    }
}

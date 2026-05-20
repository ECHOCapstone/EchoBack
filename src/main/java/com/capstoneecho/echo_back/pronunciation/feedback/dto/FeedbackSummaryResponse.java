package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackSummaryResponse(
        Long id,
        String title,
        double accuracy,
        String weakPhoneme,
        Instant createdAt
) {

    public static FeedbackSummaryResponse from(PronunciationFeedback fb) {
        return new FeedbackSummaryResponse(
                fb.getId(),
                fb.getTitle(),
                fb.getAccuracy(),
                fb.getWeakPhoneme(),
                fb.getCreatedAt());
    }
}

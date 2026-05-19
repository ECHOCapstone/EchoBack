package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;

import java.time.Instant;
import java.util.List;

public record FeedbackDetailResponse(
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

    public static FeedbackDetailResponse from(PronunciationFeedback f) {
        return new FeedbackDetailResponse(
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

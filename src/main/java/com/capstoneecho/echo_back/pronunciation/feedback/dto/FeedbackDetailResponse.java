package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeError;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeOp;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackDetailResponse(
        Long id,
        Long scriptId,
        Long sessionId,
        String title,
        double accuracy,
        String weakPhoneme,
        String practiceWord,
        String guidanceKr,
        List<PhonemeErrorView> errors,
        Instant createdAt,
        boolean completed,
        Instant completedAt
) {

    public record PhonemeErrorView(
            PhonemeOp op, String canonical, String perceived, Integer canonicalIndex) {
        public static PhonemeErrorView from(PhonemeError e) {
            return new PhonemeErrorView(
                    e.getOp(), e.getCanonical(), e.getPerceived(), e.getCanonicalIndex());
        }
    }

    public static FeedbackDetailResponse from(PronunciationFeedback fb) {
        Long scriptId = fb.getScript() == null ? null : fb.getScript().getId();
        Long sessionId = fb.getSession() == null ? null : fb.getSession().getId();
        List<PhonemeErrorView> errors = fb.getErrors().stream()
                .map(PhonemeErrorView::from)
                .toList();
        return new FeedbackDetailResponse(
                fb.getId(),
                scriptId,
                sessionId,
                fb.getTitle(),
                fb.getAccuracy(),
                fb.getWeakPhoneme(),
                fb.getPracticeWord(),
                fb.getGuidanceKr(),
                errors,
                fb.getCreatedAt(),
                fb.isCompleted(),
                fb.getCompletedAt());
    }
}

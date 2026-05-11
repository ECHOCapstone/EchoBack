package com.capstoneecho.echo_back.learning.session.dto;

import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import java.time.Instant;
import java.util.List;

public record SessionDetailResponse(
        Long id,
        String title,
        String scriptText,
        boolean favorite,
        List<SessionSentenceResponse> sentences,
        Instant createdAt,
        Instant updatedAt
) {

    public static SessionDetailResponse from(Session session) {
        List<SessionSentenceResponse> sentenceDtos = session.getSentences().stream()
                .map(SessionSentenceResponse::from)
                .toList();
        return new SessionDetailResponse(
                session.getId(),
                session.getTitle(),
                session.getScriptText(),
                session.isFavorite(),
                sentenceDtos,
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    public record SessionSentenceResponse(
            Long id,
            int sentenceIndex,
            String text
    ) {

        public static SessionSentenceResponse from(SessionSentence sentence) {
            return new SessionSentenceResponse(
                    sentence.getId(),
                    sentence.getSentenceIndex(),
                    sentence.getText());
        }
    }
}

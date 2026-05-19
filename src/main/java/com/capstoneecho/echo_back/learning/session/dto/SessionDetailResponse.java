package com.capstoneecho.echo_back.learning.session.dto;

import com.capstoneecho.echo_back.learning.session.entity.Session;

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
        return new SessionDetailResponse(
                session.getId(),
                session.getTitle(),
                session.getScriptText(),
                session.isFavorite(),
                session.getSentences().stream().map(SessionSentenceResponse::from).toList(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}

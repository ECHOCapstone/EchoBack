package com.capstoneecho.echo_back.app.session.dto;

import com.capstoneecho.echo_back.app.session.Session;

import java.time.Instant;
import java.util.List;

public record SessionResponse(
        Long id,
        String title,
        String scriptText,
        List<SessionSentenceResponse> sentences,
        Instant createdAt,
        Instant updatedAt
) {

    public static SessionResponse from(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getTitle(),
                session.getScriptText(),
                session.getSentences().stream().map(SessionSentenceResponse::from).toList(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}

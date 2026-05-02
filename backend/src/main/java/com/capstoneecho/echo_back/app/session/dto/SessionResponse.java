package com.capstoneecho.echo_back.app.session.dto;

import com.capstoneecho.echo_back.app.session.Session;

import java.time.Instant;

public record SessionResponse(
        Long id,
        String title,
        String scriptText,
        Instant createdAt,
        Instant updatedAt
) {

    public static SessionResponse from(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getTitle(),
                session.getScriptText(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}

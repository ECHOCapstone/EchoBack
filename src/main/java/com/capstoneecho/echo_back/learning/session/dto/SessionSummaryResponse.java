package com.capstoneecho.echo_back.learning.session.dto;

import com.capstoneecho.echo_back.learning.session.entity.Session;
import java.time.Instant;

public record SessionSummaryResponse(
        Long id,
        String title,
        boolean favorite,
        Instant createdAt,
        Instant updatedAt
) {

    public static SessionSummaryResponse from(Session session) {
        return new SessionSummaryResponse(
                session.getId(),
                session.getTitle(),
                session.isFavorite(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}

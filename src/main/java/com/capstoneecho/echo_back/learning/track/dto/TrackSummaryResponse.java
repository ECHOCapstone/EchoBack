package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.track.entity.Track;

public record TrackSummaryResponse(
        Long id,
        String title,
        String description,
        int displayOrder
) {

    public static TrackSummaryResponse from(Track track) {
        return new TrackSummaryResponse(
                track.getId(),
                track.getTitle(),
                track.getDescription(),
                track.getDisplayOrder()
        );
    }
}

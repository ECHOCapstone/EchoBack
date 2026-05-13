package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.track.entity.Track;

public record TrackSummaryResponse(
        Long id,
        String title,
        String description,
        int displayOrder,
        int chapterCount
) {

    public static TrackSummaryResponse of(Track track, int chapterCount) {
        return new TrackSummaryResponse(
                track.getId(),
                track.getTitle(),
                track.getDescription(),
                track.getDisplayOrder(),
                chapterCount
        );
    }
}

package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.track.entity.Track;
import java.util.List;

public record TrackDetailResponse(
        Long id,
        String title,
        String description,
        int displayOrder,
        List<ChapterSummaryResponse> chapters
) {

    public static TrackDetailResponse of(Track track, List<ChapterSummaryResponse> chapters) {
        return new TrackDetailResponse(
                track.getId(),
                track.getTitle(),
                track.getDescription(),
                track.getDisplayOrder(),
                List.copyOf(chapters)
        );
    }
}

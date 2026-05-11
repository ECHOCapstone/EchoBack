package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.track.entity.Track;
import java.util.List;

public record TrackDetailResponse(
        Long id,
        String title,
        String description,
        List<ChapterResponse> chapters
) {

    public static TrackDetailResponse of(Track track, List<ChapterResponse> chapters) {
        return new TrackDetailResponse(
                track.getId(),
                track.getTitle(),
                track.getDescription(),
                List.copyOf(chapters)
        );
    }
}

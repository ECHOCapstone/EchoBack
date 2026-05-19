package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.track.entity.Track;

// 트랙 목록 화면에 노출되는 메타. 챕터 수까지 포함하여 진입 전에 분량을 가늠하게 한다.
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

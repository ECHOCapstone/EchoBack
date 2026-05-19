package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.track.entity.Track;

import java.util.List;

// 트랙 진입 시 한 번 받아 챕터 흐름을 그릴 때 사용하는 상세 정보.
public record TrackDetailResponse(
        Long id,
        String title,
        String description,
        int displayOrder,
        List<ChapterSummaryResponse> chapters
) {

    public static TrackDetailResponse of(Track track, List<Script> chapters) {
        return new TrackDetailResponse(
                track.getId(),
                track.getTitle(),
                track.getDescription(),
                track.getDisplayOrder(),
                chapters.stream().map(ChapterSummaryResponse::from).toList()
        );
    }
}

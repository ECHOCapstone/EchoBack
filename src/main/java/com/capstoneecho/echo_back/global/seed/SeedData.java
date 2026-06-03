package com.capstoneecho.echo_back.global.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 시드 yaml 의 record 구조. InitialDataLoader 와 어드민의 "영구 저장 / 시드 재적용" 양쪽에서 사용한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SeedData {

    private SeedData() {}

    public record TracksFile(List<Track> tracks) {}

    public record Track(
            String title,
            String description,
            int displayOrder,
            List<Chapter> chapters
    ) {}

    public record Chapter(
            int chapterOrder,
            String title,
            String content,
            String difficulty,
            String practiceWord,
            String masteryBadgeName,
            List<Step> steps
    ) {}

    public record Step(
            int orderIndex,
            String kind,
            String prompt,
            String targetText
    ) {}
}

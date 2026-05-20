package com.capstoneecho.echo_back.global.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
final class SeedData {

    private SeedData() {}

    record TracksFile(List<Track> tracks) {}

    record Track(
            String title,
            String description,
            int displayOrder,
            List<Chapter> chapters
    ) {}

    record Chapter(
            int chapterOrder,
            String title,
            String content,
            String difficulty,
            String practiceWord,
            String masteryBadgeName,
            List<Step> steps
    ) {}

    record Step(
            int orderIndex,
            String kind,
            String prompt,
            String targetText
    ) {}
}

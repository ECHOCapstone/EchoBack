package com.capstoneecho.echo_back.global.seed;

import java.util.List;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.track.entity.Track;
// seed/*.json 파일과 1:1 로 매핑되는 값 객체. 도메인 엔티티와 분리되어 있어서 JSON 스키마가
// 바뀌어도 Track/Script/LearningStep 쪽은 영향이 없다.
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

    record DemoRankingFile(List<DemoEntry> entries) {}

    record DemoEntry(String nickname, double accuracy) {}
}

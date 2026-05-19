package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;

// 트랙 상세 안에 노출되는 한 챕터의 요약. scriptId 가 그대로 GET /api/scripts/{id} 키로 사용된다.
public record ChapterSummaryResponse(
        Long scriptId,
        int chapterOrder,
        String title,
        Difficulty difficulty
) {

    public static ChapterSummaryResponse from(Script script) {
        return new ChapterSummaryResponse(
                script.getId(),
                script.getChapterOrder() != null ? script.getChapterOrder() : 0,
                script.getTitle(),
                script.getDifficulty()
        );
    }
}

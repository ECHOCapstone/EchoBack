package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;

public record ChapterSummaryResponse(
        Long scriptId,
        int chapterOrder,
        String title,
        Difficulty difficulty
) {

    public static ChapterSummaryResponse from(Script script) {
        return new ChapterSummaryResponse(
                script.getId(),
                script.getChapterOrder(),
                script.getTitle(),
                script.getDifficulty()
        );
    }
}

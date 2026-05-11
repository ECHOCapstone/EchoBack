package com.capstoneecho.echo_back.learning.track.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;

public record ChapterResponse(
        Long scriptId,
        String title,
        Integer chapterOrder,
        Difficulty difficulty,
        boolean preset
) {

    public static ChapterResponse from(Script script) {
        return new ChapterResponse(
                script.getId(),
                script.getTitle(),
                script.getChapterOrder(),
                script.getDifficulty(),
                script.isPreset()
        );
    }
}

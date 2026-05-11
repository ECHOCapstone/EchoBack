package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;

public record ScriptSummaryResponse(
        Long id,
        String title,
        Difficulty difficulty,
        boolean preset
) {

    public static ScriptSummaryResponse from(Script script) {
        return new ScriptSummaryResponse(
                script.getId(),
                script.getTitle(),
                script.getDifficulty(),
                script.isPreset());
    }
}

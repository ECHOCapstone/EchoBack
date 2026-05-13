package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ScriptSummaryResponse(
        Long id,
        String title,
        Difficulty difficulty,
        @JsonProperty("isPreset") boolean isPreset
) {

    public static ScriptSummaryResponse from(Script script) {
        return new ScriptSummaryResponse(
                script.getId(),
                script.getTitle(),
                script.getDifficulty(),
                script.isPreset());
    }
}

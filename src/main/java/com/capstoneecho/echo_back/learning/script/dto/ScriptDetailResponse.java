package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScriptDetailResponse(
        Long id,
        String title,
        String content,
        Difficulty difficulty,
        @JsonProperty("isPreset") boolean isPreset,
        String practiceWord,
        String masteryBadgeName,
        List<StepResponse> steps
) {

    public static ScriptDetailResponse of(Script script, List<LearningStep> steps) {
        List<StepResponse> stepResponses = steps.stream()
                .map(StepResponse::from)
                .toList();
        return new ScriptDetailResponse(
                script.getId(),
                script.getTitle(),
                script.getContent(),
                script.getDifficulty(),
                script.isPreset(),
                script.getPracticeWord(),
                script.getMasteryBadgeName(),
                stepResponses);
    }
}

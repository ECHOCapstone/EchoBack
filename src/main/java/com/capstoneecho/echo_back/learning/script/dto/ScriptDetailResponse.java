package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import java.util.List;

public record ScriptDetailResponse(
        Long id,
        String title,
        String content,
        Difficulty difficulty,
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
                script.getPracticeWord(),
                script.getMasteryBadgeName(),
                stepResponses);
    }
}

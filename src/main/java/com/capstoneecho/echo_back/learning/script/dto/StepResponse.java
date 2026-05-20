package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;

public record StepResponse(Long id, StepKind kind, String prompt, String targetText) {

    public static StepResponse from(LearningStep step) {
        return new StepResponse(step.getId(), step.getKind(), step.getPrompt(), step.getTargetText());
    }
}

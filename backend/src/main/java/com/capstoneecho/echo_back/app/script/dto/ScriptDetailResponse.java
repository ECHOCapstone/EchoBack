package com.capstoneecho.echo_back.app.script.dto;

import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.script.Difficulty;
import com.capstoneecho.echo_back.app.script.Script;

import java.util.List;

// 학습 단위 진입 시 한 번 받아 채팅 흐름을 그릴 때 사용하는 상세 정보.
public record ScriptDetailResponse(
        Long id,
        String title,
        String content,
        Difficulty difficulty,
        boolean isPreset,
        List<StepResponse> steps
) {

    public static ScriptDetailResponse of(Script script, List<LearningStep> steps) {
        return new ScriptDetailResponse(
                script.getId(),
                script.getTitle(),
                script.getContent(),
                script.getDifficulty(),
                Boolean.TRUE.equals(script.getIsPreset()),
                steps.stream().map(StepResponse::from).toList()
        );
    }
}

package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

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

    // 한국어 번역 맵을 받지 않는 기본 빌더 — 어드민 편집 응답처럼 자막이 필요 없는 흐름용.
    public static ScriptDetailResponse of(Script script, List<LearningStep> steps) {
        return of(script, steps, Map.of());
    }

    // 한국어 번역 맵 (영문 targetText → 한국어) 을 받아 StepResponse 마다 자막을 함께 실어 응답한다.
    // 맵에 없거나 step.targetText 가 null 이면 자막 없이 응답한다 (FE 가 자막 줄을 생략).
    public static ScriptDetailResponse of(
            Script script, List<LearningStep> steps, Map<String, String> translationsByTarget) {
        List<StepResponse> stepResponses = steps.stream()
                .map(step -> StepResponse.from(
                        step,
                        step.getTargetText() == null ? null : translationsByTarget.get(step.getTargetText())))
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

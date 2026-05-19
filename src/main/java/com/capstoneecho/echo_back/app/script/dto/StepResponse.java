package com.capstoneecho.echo_back.app.script.dto;

import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.learning.StepKind;

// 채팅형 학습 UI 의 한 메시지에 대응되는 DTO.
// 정답 음소는 도메인이 보관하지 않으므로 응답에도 노출되지 않는다.
public record StepResponse(
        Long id,
        int orderIndex,
        StepKind kind,
        String prompt,
        String targetText
) {

    public static StepResponse from(LearningStep step) {
        return new StepResponse(
                step.getId(),
                step.getOrderIndex(),
                step.getKind(),
                step.getPrompt(),
                step.getTargetText()
        );
    }
}

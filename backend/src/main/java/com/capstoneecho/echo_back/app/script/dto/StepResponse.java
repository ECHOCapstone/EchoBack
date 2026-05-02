package com.capstoneecho.echo_back.app.script.dto;

import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.learning.StepKind;

import java.util.List;

// 채팅형 학습 UI 의 한 메시지에 대응되는 DTO.
// canonicalPhonemes 는 RECORD 단계의 모델 평가용 정답 음소 배열이다.
public record StepResponse(
        Long id,
        int orderIndex,
        StepKind kind,
        String prompt,
        String targetText,
        List<String> canonicalPhonemes
) {

    public static StepResponse from(LearningStep step) {
        return new StepResponse(
                step.getId(),
                step.getOrderIndex(),
                step.getKind(),
                step.getPrompt(),
                step.getTargetText(),
                splitPhonemes(step.getCanonicalPhonemes())
        );
    }

    private static List<String> splitPhonemes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.trim().split("\\s+"));
    }
}

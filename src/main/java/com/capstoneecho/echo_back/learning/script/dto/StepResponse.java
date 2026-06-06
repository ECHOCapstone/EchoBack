package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;

// 학습 한 단계의 응답. targetText 가 있으면 koreanTranslation 에 한국어 자막을 함께 실어 FE 가
// 별도 호출 없이 즉시 노출할 수 있게 한다. 번역이 없거나 INTRO 단계처럼 발화 텍스트가 없으면 null.
public record StepResponse(
        Long id,
        StepKind kind,
        String prompt,
        String targetText,
        String koreanTranslation) {

    public static StepResponse from(LearningStep step, String koreanTranslation) {
        return new StepResponse(
                step.getId(),
                step.getKind(),
                step.getPrompt(),
                step.getTargetText(),
                emptyToNull(koreanTranslation));
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 스크립트 한 단계. RECORD 는 targetText 가 필수이며, 검증은 LearningStep 팩토리에서 수행한다.
public record AdminStepRequest(
        @NotNull StepKind kind,
        @NotBlank String prompt,
        String targetText
) {}

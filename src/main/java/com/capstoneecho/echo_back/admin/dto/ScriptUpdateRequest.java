package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

// 스크립트 수정 (전체 교체). chapterOrder 는 기존 값을 유지하고, steps 는 새 목록으로 갈아끼운다.
public record ScriptUpdateRequest(
        @NotBlank String title,
        @NotBlank String content,
        Difficulty difficulty,
        String practiceWord,
        String masteryBadgeName,
        @NotEmpty @Valid List<AdminStepRequest> steps
) {}

package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// 트랙 챕터 스크립트 생성. trackId 트랙의 마지막 챕터 뒤에 추가된다 (chapterOrder 는 서비스가 자동 부여).
// steps 는 최소 1개 — 스텝이 없는 스크립트는 학습할 수 없다.
public record ScriptCreateRequest(
        @NotNull Long trackId,
        @NotBlank String title,
        @NotBlank String content,
        Difficulty difficulty,
        String practiceWord,
        String masteryBadgeName,
        @NotEmpty @Valid List<AdminStepRequest> steps
) {}

package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotBlank;

// 프롬프트 본문 재정의 요청.
public record PromptUpdateRequest(@NotBlank String content) {}

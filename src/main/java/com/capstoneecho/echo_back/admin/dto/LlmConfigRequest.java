package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotBlank;

// LLM 설정 변경 요청. provider 는 필수, model 은 gemini 일 때만 의미가 있다.
public record LlmConfigRequest(
        @NotBlank String provider,
        String model
) {}

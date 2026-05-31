package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotBlank;

// 음소인식 모델 선택 변경 요청. model 은 모델 서버 /models 후보 중 하나의 id.
public record ModelServerConfigRequest(
        @NotBlank String model
) {}

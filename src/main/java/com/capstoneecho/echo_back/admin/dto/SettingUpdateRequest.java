package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotNull;

// 설정 값 재정의 요청. 형식 검증(INT/DOUBLE)은 서비스가 수행한다.
public record SettingUpdateRequest(@NotNull String value) {}

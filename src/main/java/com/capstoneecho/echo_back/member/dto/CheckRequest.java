package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.NotBlank;

// 단일 필드 중복 확인 요청. value 의 검증은 엔드포인트별 서비스에서 추가로 수행한다.
public record CheckRequest(@NotBlank String value) {}

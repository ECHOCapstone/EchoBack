package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotBlank;

// 트랙 수정 요청 (전체 교체). 모든 필드를 현재 값으로 채워 보낸다.
public record TrackUpdateRequest(
        @NotBlank String title,
        String description,
        int displayOrder
) {}

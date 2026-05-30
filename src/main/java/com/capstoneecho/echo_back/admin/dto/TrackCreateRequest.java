package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotBlank;

// 트랙 생성 요청. description 은 선택, displayOrder 는 목록 정렬 순서.
public record TrackCreateRequest(
        @NotBlank String title,
        String description,
        int displayOrder
) {}

package com.capstoneecho.echo_back.app.session.dto;

import jakarta.validation.constraints.Size;

// 부분 업데이트 요청. null 인 필드는 변경하지 않는다.
// favorite 는 Boolean 박싱이라 null 이면 토글 무시, true/false 면 명시 반영.
public record SessionUpdateRequest(
        @Size(max = 100) String title,
        @Size(max = 5000) String scriptText,
        Boolean favorite
) {}

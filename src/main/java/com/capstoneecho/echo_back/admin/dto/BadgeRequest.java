package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 어드민이 새 배지를 만들거나 수정할 때 보내는 요청.
//   id 는 생성 시에만 의미가 있고, PATCH 는 URI 의 id 를 단일 출처로 본다.
//   condition 의 유효 값 검증은 서비스가 BadgeCondition 으로 한다.
public record BadgeRequest(
        @NotBlank
        @Size(max = 64)
        String id,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 64)
        String condition,

        @PositiveOrZero
        int threshold
) {
}

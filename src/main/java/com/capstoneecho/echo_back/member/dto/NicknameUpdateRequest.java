package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// SignupRequest.nickname 과 동일한 30자 상한을 유지한다 (users.nickname 컬럼과도 정합).
public record NicknameUpdateRequest(
        @NotBlank
        @Size(max = 30)
        String nickname
) {
}

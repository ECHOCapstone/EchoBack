package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
        @NotBlank
        @Size(max = 20)
        String nickname
) {
}

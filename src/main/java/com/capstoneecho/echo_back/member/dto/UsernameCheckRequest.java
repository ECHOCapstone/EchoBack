package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsernameCheckRequest(
        @NotBlank
        @Size(min = 1, max = 100)
        String value
) {
}

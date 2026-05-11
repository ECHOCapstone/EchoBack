package com.capstoneecho.echo_back.learning.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SessionCreateRequest(
        @NotBlank
        @Size(max = 100)
        String title
) {
}

package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailCheckRequest(
        @NotBlank
        @Email
        @Size(max = 100)
        String email
) {
}

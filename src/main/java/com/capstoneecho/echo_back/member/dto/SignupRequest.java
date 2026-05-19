package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$", message = "영문/숫자/_ 3~30자")
        String username,

        @NotBlank
        @Size(min = 6, max = 50)
        String password,

        @NotBlank
        @Size(max = 30)
        String nickname,

        @NotBlank
        @Email
        String email,

        @AssertTrue(message = "서비스 이용약관 동의가 필요합니다.")
        boolean agreedTerms
) {}

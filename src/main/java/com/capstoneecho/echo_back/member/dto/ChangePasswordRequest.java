package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 로그인된 사용자가 비밀번호를 변경할 때 보내는 입력.
//   currentPassword: 본인 확인용. BCrypt 매칭에 사용한다.
//   newPassword    : 새 비밀번호. PasswordPolicyEvaluator 가 정책 검증을 수행한다.
public record ChangePasswordRequest(
        @NotBlank
        @Size(max = 100)
        String currentPassword,

        @NotBlank
        @Size(max = 100)
        String newPassword
) {
}

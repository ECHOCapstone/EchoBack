package com.capstoneecho.echo_back.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 어드민이 한 종류 약관 본문을 갱신할 때 보내는 입력.
//   body 는 마크다운 본문 (회원가입 모달이 그대로 표시).
public record LegalTermsUpdateRequest(
        @NotBlank
        @Size(max = 100_000)
        String body
) {
}

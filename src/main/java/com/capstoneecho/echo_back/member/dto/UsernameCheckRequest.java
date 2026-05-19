package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 아이디 (username) 중복 확인 요청. EmailCheckRequest 와 검증 한도가 같지만
// 컨트롤러 메서드 시그니처에서 의도가 드러나도록 분리해 둔다.
public record UsernameCheckRequest(
        @NotBlank
        @Size(min = 1, max = 100)
        String value
) {}

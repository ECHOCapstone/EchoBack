package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 이메일 중복 확인 요청. 단일 필드라 같은 형태인 UsernameCheckRequest 와 별도로 두어
// 컨트롤러 메서드 시그니처에서 의도 (이메일 vs 아이디) 가 드러나게 한다.
public record EmailCheckRequest(
        @NotBlank
        @Size(max = 100)
        String value
) {}

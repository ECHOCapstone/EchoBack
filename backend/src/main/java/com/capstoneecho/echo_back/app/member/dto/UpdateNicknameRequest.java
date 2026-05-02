package com.capstoneecho.echo_back.app.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 사용자가 자신의 닉네임을 직접 갱신할 때 보내는 요청. 공백만 입력하는 것을 막기 위해 NotBlank 를 둔다.
public record UpdateNicknameRequest(
        @NotBlank
        @Size(max = 30)
        String nickname
) {}

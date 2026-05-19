package com.capstoneecho.echo_back.app.auth.dto;

import com.capstoneecho.echo_back.app.member.dto.UserResponse;

// 로그인/회원가입 성공 시 프론트에 내려주는 JWT 발급 응답.
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSec,
        UserResponse user
) {

    public static TokenResponse of(String accessToken, long expiresInSec, UserResponse user) {
        return new TokenResponse(accessToken, "Bearer", expiresInSec, user);
    }
}

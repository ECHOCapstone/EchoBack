package com.capstoneecho.echo_back.member.dto;

import com.capstoneecho.echo_back.member.dto.UserResponse;

// 로그인/회원가입 성공 시 프론트에 내려주는 JWT 발급 응답.
public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSec,
        UserResponse user
) {

    public static final String BEARER = "Bearer";

    public static AuthTokenResponse of(String accessToken, long expiresInSec, UserResponse user) {
        return new AuthTokenResponse(accessToken, BEARER, expiresInSec, user);
    }
}

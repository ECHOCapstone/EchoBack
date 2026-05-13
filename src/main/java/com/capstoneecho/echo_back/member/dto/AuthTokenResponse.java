package com.capstoneecho.echo_back.member.dto;

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

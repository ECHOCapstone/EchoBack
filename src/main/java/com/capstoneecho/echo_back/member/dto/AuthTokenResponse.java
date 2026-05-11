package com.capstoneecho.echo_back.member.dto;

import java.time.Instant;

public record AuthTokenResponse(
        String token,
        Instant expiresAt,
        MemberProfileResponse profile
) {
}

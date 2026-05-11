package com.capstoneecho.echo_back.global.jwt;

import java.util.Objects;

public record JwtPrincipal(Long userId, String username, String email) {

    public JwtPrincipal {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}

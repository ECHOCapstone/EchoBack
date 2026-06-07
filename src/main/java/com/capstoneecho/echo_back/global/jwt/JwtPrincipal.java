package com.capstoneecho.echo_back.global.jwt;

import java.util.Objects;

// 인증된 요청 주체. role 은 권한 식별자 문자열("USER" / "ADMIN") 로, global 이 member 도메인 enum 에
// 의존하지 않도록 원시 형태로 보관한다. 미지정 시 USER 로 본다.
public record JwtPrincipal(Long userId, String username, String email, String role) {

    public JwtPrincipal {
        Objects.requireNonNull(userId, "userId must not be null");
        if (role == null || role.isBlank()) {
            role = "USER";
        }
    }
}

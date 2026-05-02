package com.capstoneecho.echo_back.app.jwt;

// 인증된 요청의 SecurityContext 에 저장되는 식별자. userId 와 username 만 보관하고,
// 비밀번호·민감정보는 포함하지 않는다.
public record JwtPrincipal(Long userId, String username) {}

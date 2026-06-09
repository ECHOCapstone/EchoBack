package com.capstoneecho.echo_back.member.dto;

import com.capstoneecho.echo_back.member.entity.User;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String nickname,
        int streak,
        int exp,
        String role,
        // 비밀번호 해시가 채워진 계정인지 여부. OAuth 전용 가입자는 false 로 내려가, 프론트가
        // 비밀번호 변경 / 탈퇴 시 본인 확인 칸을 숨기거나 안내한다.
        boolean hasPassword,
        Instant createdAt,
        // 온보딩 튜토리얼 완료 여부. false 면 프론트가 최초 1회 튜토리얼을 자동 노출한다.
        boolean onboardingCompleted
) {

    public static UserResponse from(User user) {
        String hash = user.getPasswordHash();
        boolean hasPassword = hash != null && !hash.isBlank();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getStreak(),
                user.getExp(),
                user.getRole().name(),
                hasPassword,
                user.getCreatedAt(),
                user.isOnboardingCompleted()
        );
    }
}

package com.capstoneecho.echo_back.member.dto;

import com.capstoneecho.echo_back.member.entity.User;

import java.time.Instant;

// 클라이언트에 노출 가능한 사용자 정보. 비밀번호 해시는 절대 포함하지 않는다.
public record UserResponse(
        Long id,
        String username,
        String email,
        String nickname,
        int streak,
        int exp,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getStreak(),
                user.getExp(),
                user.getCreatedAt()
        );
    }
}

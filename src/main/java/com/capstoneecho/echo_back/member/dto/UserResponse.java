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
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}

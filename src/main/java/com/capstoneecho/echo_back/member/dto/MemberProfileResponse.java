package com.capstoneecho.echo_back.member.dto;

import com.capstoneecho.echo_back.member.entity.User;
import java.time.Instant;

public record MemberProfileResponse(
        Long id,
        String username,
        String email,
        String nickname,
        int streak,
        int exp,
        Instant lastStudyAt
) {

    public static MemberProfileResponse from(User user) {
        return new MemberProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getStreak(),
                user.getExp(),
                user.getLastStudyAt()
        );
    }
}

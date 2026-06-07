package com.capstoneecho.echo_back.challenge.dto;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import java.time.Instant;

// 어드민의 챌린지 목록 한 행. active 토글과 종료 시점을 함께 노출해 운영자가 한 화면에서 관리한다.
public record AdminChallengeListItem(
        Long id,
        String targetText,
        String koreanTranslation,
        boolean active,
        Instant createdAt,
        Instant activatedAt,
        Instant deactivatedAt,
        long totalAttempts,
        long totalParticipants
) {
    public static AdminChallengeListItem of(
            DailyChallenge challenge, long totalAttempts, long totalParticipants) {
        return new AdminChallengeListItem(
                challenge.getId(),
                challenge.getTargetText(),
                challenge.getKoreanTranslation(),
                challenge.isActive(),
                challenge.getCreatedAt(),
                challenge.getActivatedAt(),
                challenge.getDeactivatedAt(),
                totalAttempts,
                totalParticipants);
    }
}

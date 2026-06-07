package com.capstoneecho.echo_back.challenge.dto;

import java.time.Instant;
import java.util.List;

// "지난 챌린지" 페이지의 한 행.
//   id, targetText, koreanTranslation : 챌린지 본문.
//   activatedAt / deactivatedAt        : 챌린지 기간 표기.
//   topEntries                         : 1~3등 미니뷰 — 챌린지 종료 직후 닫혀 있어도 결과를 그대로 노출한다.
//   myBestScore                        : 본인 best 점수 (미도전이면 null).
//   myRank                             : 본인 순위 (미도전이면 0).
//   myBadgeId                          : 본인이 받은 배지 식별자 (없으면 null).
public record ChallengeHistoryItem(
        Long id,
        String targetText,
        String koreanTranslation,
        Instant activatedAt,
        Instant deactivatedAt,
        List<TopEntry> topEntries,
        Double myBestScore,
        int myRank,
        String myBadgeId
) {

    public record TopEntry(int rank, String nickname, double bestScore, String badgeId) {}
}

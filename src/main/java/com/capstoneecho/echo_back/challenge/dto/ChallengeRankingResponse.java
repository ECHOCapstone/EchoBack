package com.capstoneecho.echo_back.challenge.dto;

import java.util.List;

// 활성 챌린지의 사용자별 best 점수 랭킹.
//   challengeId / targetText  : 어떤 챌린지의 랭킹인지 — FE 가 카드 헤더에 노출.
//   entries                   : 상위 N 명. rank 는 1 부터.
//   totalRanked               : 한 번이라도 도전한 사용자 수.
//   myRank                    : 본인 순위. 미도전이면 0.
//   myBestScore               : 본인 best 점수. 미도전이면 null.
//   myAttempts                : 본인 총 시도 횟수 (오늘 + 누적).
//   myEntryShown              : entries 안에 본인이 포함됐는지 — false 면 FE 가 별도 줄로 본인을 그린다.
public record ChallengeRankingResponse(
        Long challengeId,
        String targetText,
        int totalRanked,
        int myRank,
        Double myBestScore,
        long myAttempts,
        boolean myEntryShown,
        List<Entry> entries
) {

    public record Entry(
            int rank,
            String nickname,
            double bestScore,
            long attempts,
            boolean isMe
    ) {}
}

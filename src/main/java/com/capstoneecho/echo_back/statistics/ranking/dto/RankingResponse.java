package com.capstoneecho.echo_back.statistics.ranking.dto;

import java.util.List;

// 주간 발음 정확도 랭킹 응답.
//   period             : 사용자에게 보일 기간 표기 ("5/30 ~ 6/5").
//   windowDays         : 집계에 쓰인 일수 — 정책 변경 추적 / FE 텍스트 조립용.
//   minActivityCount   : 랭킹 진입 임계 — FE 가 "X건 더 학습하면 랭킹 합류" 안내에 사용.
//   totalRanked        : 임계를 통과한 전체 사용자 수 (Top N 과 무관).
//   myRank             : 본인 순위 — 임계 미통과 / 활동 없음이면 0.
//   myAccuracy         : 본인 평균 정확도 — 임계 미통과여도 자기 진척도는 알려준다.
//   myActivityCount    : 본인의 윈도 내 완료 건수.
//   myEntryShown       : entries 안에 본인이 포함됐는지. false 면 FE 가 별도 줄로 본인 행을 노출한다.
//   entries            : Top N 행. activityCount 로 "X 회 학습" 부제를 함께 보여준다.
public record RankingResponse(
        String period,
        int windowDays,
        int minActivityCount,
        int totalRanked,
        int myRank,
        double myAccuracy,
        int myActivityCount,
        boolean myEntryShown,
        List<Entry> entries
) {

    public record Entry(
            int rank,
            String nickname,
            double accuracy,
            int activityCount,
            boolean isMe
    ) {}
}

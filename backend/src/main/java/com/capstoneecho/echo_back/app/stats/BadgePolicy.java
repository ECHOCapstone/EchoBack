package com.capstoneecho.echo_back.app.stats;

import com.capstoneecho.echo_back.app.feedback.PronunciationFeedback;
import com.capstoneecho.echo_back.app.stats.dto.StatsResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

// 보유/미보유 배지 판정의 단일 정책 진입점.
//
// 입력은 사용자의 누적 피드백, 현재 streak, 맞춤 학습 세션 수.
// 정책이 추가될 때 본 클래스 한 곳만 수정하면 된다 (OCP).
@Component
class BadgePolicy {

    // 챕터 마스터 판정 임계 정확도. 종합 피드백 한 회차라도 이 값을 넘으면 마스터로 인정한다.
    private static final double MASTER_THRESHOLD = 80.0;
    // "완벽한 한 판" 배지 임계 정확도.
    private static final double PERFECT_THRESHOLD = 95.0;
    // "잰말놀이 N회" 배지 임계 횟수.
    private static final int TONGUE_TWISTER_GOAL = 5;
    // 맞춤 학습 마스터 임계 세션 수.
    private static final int SESSION_MASTER_GOAL = 5;

    List<StatsResponse.Badge> evaluate(
            List<PronunciationFeedback> feedbacks,
            int currentStreak,
            long totalSessions
    ) {
        var ordered = new LinkedHashMap<String, BadgeDef>();
        ordered.put("first_step", new BadgeDef("첫 학습 완료", !feedbacks.isEmpty()));
        ordered.put("streak_3", new BadgeDef("3일 연속 출석", currentStreak >= 3));
        ordered.put("streak_7", new BadgeDef("7일 연속 출석", currentStreak >= 7));
        ordered.put("streak_14", new BadgeDef("2주 연속 출석", currentStreak >= 14));
        ordered.put("streak_30", new BadgeDef("30일 연속 출석", currentStreak >= 30));
        ordered.put("tongue_twister_5",
                new BadgeDef("잰말놀이 " + TONGUE_TWISTER_GOAL + "회 완료",
                        countByTitleContains(feedbacks, "잰말") >= TONGUE_TWISTER_GOAL));
        ordered.put("master_rl", new BadgeDef("R vs L 마스터", hasMastered(feedbacks, "R vs L")));
        ordered.put("master_vb", new BadgeDef("V vs B 마스터", hasMastered(feedbacks, "V vs B")));
        ordered.put("master_fp", new BadgeDef("F vs P 마스터", hasMastered(feedbacks, "F vs P")));
        ordered.put("master_th", new BadgeDef("TH 마스터", hasMastered(feedbacks, "TH")));
        ordered.put("session_starter", new BadgeDef("맞춤 학습 도전자", totalSessions >= 1));
        ordered.put("session_master",
                new BadgeDef("맞춤 학습 마스터", totalSessions >= SESSION_MASTER_GOAL));
        ordered.put("perfect_unit",
                new BadgeDef("완벽한 한 판",
                        feedbacks.stream().anyMatch(f -> f.getAccuracy() >= PERFECT_THRESHOLD)));

        return ordered.entrySet().stream()
                .map(e -> new StatsResponse.Badge(e.getKey(), e.getValue().name(), e.getValue().achieved()))
                .toList();
    }

    private long countByTitleContains(List<PronunciationFeedback> feedbacks, String keyword) {
        return feedbacks.stream()
                .filter(f -> f.getTitle() != null && f.getTitle().contains(keyword))
                .count();
    }

    private boolean hasMastered(List<PronunciationFeedback> feedbacks, String titleKeyword) {
        return feedbacks.stream()
                .anyMatch(f -> f.getTitle() != null
                        && f.getTitle().contains(titleKeyword)
                        && f.getAccuracy() >= MASTER_THRESHOLD);
    }

    private record BadgeDef(String name, boolean achieved) {}
}

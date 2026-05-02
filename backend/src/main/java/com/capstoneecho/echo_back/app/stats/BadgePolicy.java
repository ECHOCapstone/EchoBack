package com.capstoneecho.echo_back.app.stats;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.feedback.PronunciationFeedback;
import com.capstoneecho.echo_back.app.stats.dto.StatsResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

// 보유/미보유 배지 판정의 단일 정책 진입점.
//
// 입력은 사용자의 누적 피드백, 현재 streak, 맞춤 학습 세션 수.
// 임계값은 모두 application.yaml 의 app.badge 에서 주입되어 코드 재배포 없이 조정 가능하다.
// 정책이 추가될 때 본 클래스 한 곳만 수정하면 된다 (OCP).
@Component
class BadgePolicy {

    private final double masterThreshold;
    private final double perfectThreshold;
    private final int tongueTwisterGoal;
    private final int sessionMasterGoal;

    BadgePolicy(AppProperties properties) {
        var badge = properties.badge();
        this.masterThreshold = badge.masterThreshold();
        this.perfectThreshold = badge.perfectThreshold();
        this.tongueTwisterGoal = badge.tongueTwisterGoal();
        this.sessionMasterGoal = badge.sessionMasterGoal();
    }

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
                new BadgeDef("잰말놀이 " + tongueTwisterGoal + "회 완료",
                        countByTitleContains(feedbacks, "잰말") >= tongueTwisterGoal));
        ordered.put("master_rl", new BadgeDef("R vs L 마스터", hasMastered(feedbacks, "R vs L")));
        ordered.put("master_vb", new BadgeDef("V vs B 마스터", hasMastered(feedbacks, "V vs B")));
        ordered.put("master_fp", new BadgeDef("F vs P 마스터", hasMastered(feedbacks, "F vs P")));
        ordered.put("master_th", new BadgeDef("TH 마스터", hasMastered(feedbacks, "TH")));
        ordered.put("session_starter", new BadgeDef("맞춤 학습 도전자", totalSessions >= 1));
        ordered.put("session_master",
                new BadgeDef("맞춤 학습 마스터", totalSessions >= sessionMasterGoal));
        ordered.put("perfect_unit",
                new BadgeDef("완벽한 한 판",
                        feedbacks.stream().anyMatch(f -> f.getAccuracy() >= perfectThreshold)));

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
                        && f.getAccuracy() >= masterThreshold);
    }

    private record BadgeDef(String name, boolean achieved) {}
}

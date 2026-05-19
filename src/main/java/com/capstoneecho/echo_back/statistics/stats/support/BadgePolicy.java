package com.capstoneecho.echo_back.statistics.stats.support;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import com.capstoneecho.echo_back.learning.script.service.ScriptService;
// 보유/미보유 배지 판정의 단일 정책 진입점.
//
// 입력은 사용자의 누적 피드백, 현재 streak, 맞춤 학습 세션 수.
// 임계값은 application.yaml 의 app.badge 에서 주입되어 코드 재배포 없이 조정 가능하다.
//
// 챕터 단위 마스터 배지 (master_<scriptId>) 는 ScriptService 가 돌려주는 시드 챕터 목록을
// 그대로 따라 동적으로 생성된다 — 챕터를 추가/제거할 때 본 클래스를 수정할 필요가 없다.
@Component
public class BadgePolicy {

    // 잰말놀이 챕터는 빈도형 챌린지(N회 완료) 라 마스터 평가 대상이 아니다. 챕터 제목에 포함되는
    // 한국어 키워드로 식별하며, 다국어/다른 챌린지 챕터가 늘어나면 별도 컬럼으로 격상한다.
    private static final String TONGUE_TWISTER_KEYWORD = "잰말";

    private final ScriptService scriptService;
    private final double masterThreshold;
    private final double perfectThreshold;
    private final int tongueTwisterGoal;
    private final int sessionMasterGoal;

    BadgePolicy(ScriptService scriptService, AppProperties properties) {
        this.scriptService = scriptService;
        var badge = properties.badge();
        this.masterThreshold = badge.masterThreshold();
        this.perfectThreshold = badge.perfectThreshold();
        this.tongueTwisterGoal = badge.tongueTwisterGoal();
        this.sessionMasterGoal = badge.sessionMasterGoal();
    }

    public List<StatsResponse.Badge> evaluate(
            List<PronunciationFeedback> feedbacks,
            int currentStreak,
            long totalSessions
    ) {
        var badges = new ArrayList<StatsResponse.Badge>();
        badges.add(badge("first_step", "첫 학습 완료", !feedbacks.isEmpty()));
        badges.add(badge("streak_3", "3일 연속 출석", currentStreak >= 3));
        badges.add(badge("streak_7", "7일 연속 출석", currentStreak >= 7));
        badges.add(badge("streak_14", "2주 연속 출석", currentStreak >= 14));
        badges.add(badge("streak_30", "30일 연속 출석", currentStreak >= 30));
        badges.add(badge(
                "tongue_twister_" + tongueTwisterGoal,
                "잰말놀이 " + tongueTwisterGoal + "회 완료",
                countByTitleContains(feedbacks, TONGUE_TWISTER_KEYWORD) >= tongueTwisterGoal
        ));
        for (var chapter : scriptService.listMasteryChapters()) {
            badges.add(masteryBadge(chapter, feedbacks));
        }
        badges.add(badge("session_starter", "맞춤 학습 도전자", totalSessions >= 1));
        badges.add(badge(
                "session_master",
                "맞춤 학습 마스터",
                totalSessions >= sessionMasterGoal
        ));
        badges.add(badge(
                "perfect_unit",
                "완벽한 한 판",
                feedbacks.stream().anyMatch(f -> f.getAccuracy() >= perfectThreshold)
        ));
        return badges;
    }

    // 한 챕터의 마스터 여부는 그 scriptId 를 가진 PronunciationFeedback 중 임계 정확도를 넘은 것이
    // 한 번이라도 있으면 인정한다. 배지 ID 는 scriptId 기반이라 챕터 추가에도 안정적이다.
    private StatsResponse.Badge masteryBadge(Script chapter, List<PronunciationFeedback> feedbacks) {
        var achieved = feedbacks.stream()
                .anyMatch(f -> chapter.getId().equals(f.getScriptId())
                        && f.getAccuracy() >= masterThreshold);
        return badge("master_" + chapter.getId(), chapter.getMasteryBadgeName(), achieved);
    }

    private long countByTitleContains(List<PronunciationFeedback> feedbacks, String keyword) {
        return feedbacks.stream()
                .filter(f -> f.getTitle() != null && f.getTitle().contains(keyword))
                .count();
    }

    private StatsResponse.Badge badge(String id, String name, boolean achieved) {
        return new StatsResponse.Badge(id, name, achieved);
    }
}

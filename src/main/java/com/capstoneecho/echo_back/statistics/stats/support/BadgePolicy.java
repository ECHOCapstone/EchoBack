package com.capstoneecho.echo_back.statistics.stats.support;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse.Badge;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// 배지 평가 단일 출처. 배지 ID / 이름 / 조건은 app.stats.badges 에서 주입한다.
// condition 식별자가 평가 가능한 enum 중 하나면 임계값과 비교해 달성 여부를 채운다.
@Component
public class BadgePolicy {

    private static final String COND_COMPLETED_FEEDBACK_COUNT = "COMPLETED_FEEDBACK_COUNT";
    private static final String COND_USER_STREAK = "USER_STREAK";

    private final List<AppProperties.Stats.Badge> badgeDefs;

    public BadgePolicy(AppProperties appProperties) {
        AppProperties.Stats stats = appProperties.stats();
        this.badgeDefs = stats == null ? List.of() : stats.safeBadges();
    }

    public List<Badge> evaluate(User user, long completedFeedbackCount) {
        List<Badge> result = new ArrayList<>(badgeDefs.size());
        for (AppProperties.Stats.Badge def : badgeDefs) {
            result.add(new Badge(def.id(), def.name(), achieved(def, user, completedFeedbackCount)));
        }
        return result;
    }

    private boolean achieved(AppProperties.Stats.Badge def, User user, long completedFeedbackCount) {
        return switch (def.condition() == null ? "" : def.condition()) {
            case COND_COMPLETED_FEEDBACK_COUNT -> completedFeedbackCount >= def.threshold();
            case COND_USER_STREAK -> user != null && user.getStreak() >= def.threshold();
            default -> false;
        };
    }
}

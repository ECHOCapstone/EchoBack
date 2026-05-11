package com.capstoneecho.echo_back.statistics.stats.support;

import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse.Badge;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BadgePolicy {

    static final int STREAK_FULL = 7;

    public List<Badge> evaluate(User user, long completedFeedbackCount) {
        List<Badge> badges = new ArrayList<>();
        badges.add(new Badge(
                "FIRST_FEEDBACK", "첫 피드백", completedFeedbackCount >= 1));
        badges.add(new Badge(
                "STREAK_7", "7일 연속 학습", user != null && user.getStreak() >= STREAK_FULL));
        return badges;
    }
}

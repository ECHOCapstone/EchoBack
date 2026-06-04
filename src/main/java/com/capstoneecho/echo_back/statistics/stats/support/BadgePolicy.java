package com.capstoneecho.echo_back.statistics.stats.support;

import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.statistics.badges.BadgeCondition;
import com.capstoneecho.echo_back.statistics.badges.BadgeDef;
import com.capstoneecho.echo_back.statistics.badges.BadgeRegistry;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse.Badge;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// 배지 평가 단일 출처. 정의 자체는 BadgeRegistry 가 (런타임 변경 가능) 갖고 있고, 평가 시
// condition 별로 user 의 누적 metric 과 threshold 를 비교한다.
@Component
public class BadgePolicy {

    private final BadgeRegistry badgeRegistry;
    private final RecordingRepository recordingRepository;

    public BadgePolicy(BadgeRegistry badgeRegistry, RecordingRepository recordingRepository) {
        this.badgeRegistry = badgeRegistry;
        this.recordingRepository = recordingRepository;
    }

    public List<Badge> evaluate(User user, long completedFeedbackCount) {
        List<BadgeDef> defs = badgeRegistry.list();
        List<Badge> result = new ArrayList<>(defs.size());
        // 평가 metric 들. 사용되는 condition 이 적으면 lazy 평가가 낫지만, 호출은 stats 화면 진입 시 1회뿐이라
        // 일괄 계산해도 비용이 작다.
        long recordingCount = user == null ? 0
                : recordingRepository.countByUser_Id(user.getId());
        int userExp = user == null ? 0 : user.getExp();
        int userStreak = user == null ? 0 : user.getStreak();
        for (BadgeDef def : defs) {
            result.add(new Badge(def.id(), def.name(),
                    achieved(def, completedFeedbackCount, recordingCount, userExp, userStreak)));
        }
        return result;
    }

    private boolean achieved(BadgeDef def, long completedFeedbackCount, long recordingCount,
                             int userExp, int userStreak) {
        if (!BadgeCondition.isKnown(def.condition())) {
            return false;
        }
        BadgeCondition condition = BadgeCondition.valueOf(def.condition());
        return switch (condition) {
            case COMPLETED_FEEDBACK_COUNT -> completedFeedbackCount >= def.threshold();
            case USER_STREAK -> userStreak >= def.threshold();
            case TOTAL_EXP -> userExp >= def.threshold();
            case RECORDING_COUNT -> recordingCount >= def.threshold();
        };
    }
}

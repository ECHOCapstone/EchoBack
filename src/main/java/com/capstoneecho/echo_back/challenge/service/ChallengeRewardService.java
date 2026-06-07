package com.capstoneecho.echo_back.challenge.service;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.entity.DailyChallengeReward;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository.UserBestProjection;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRewardRepository;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 챌린지 종료 시점에 1~3등 사용자에게 배지를 발급한다. 배지 식별자는 application.yaml 의
// app.challenge.rank-badge.* 에서 도출하며, 빈 문자열로 두면 그 등수는 발급하지 않는다.
// (challenge, user) 유니크 제약으로 같은 챌린지에 중복 발급되는 일은 DB 가 차단한다.
@Service
@Transactional
public class ChallengeRewardService {

    private static final int TOP_RANK_FOR_REWARD = 3;

    private final DailyChallengeAttemptRepository attemptRepository;
    private final DailyChallengeRewardRepository rewardRepository;
    private final UserRepository userRepository;
    private final AppProperties.Challenge.RankBadge badgeConfig;

    public ChallengeRewardService(
            DailyChallengeAttemptRepository attemptRepository,
            DailyChallengeRewardRepository rewardRepository,
            UserRepository userRepository,
            AppProperties appProperties) {
        this.attemptRepository = attemptRepository;
        this.rewardRepository = rewardRepository;
        this.userRepository = userRepository;
        AppProperties.Challenge challenge = appProperties.challenge();
        this.badgeConfig = challenge == null ? null : challenge.rankBadge();
    }

    // 챌린지의 사용자별 best 점수 상위 3명에게 배지를 발급한다. 한 챌린지에 중복 호출되어도
    // existsByChallenge_Id 가드로 1회만 적용된다.
    public void awardRanksForChallenge(DailyChallenge challenge) {
        if (challenge == null || rewardRepository.existsByChallenge_Id(challenge.getId())) {
            return;
        }
        List<UserBestProjection> bests = attemptRepository.findUserBestsByChallengeId(challenge.getId());
        int count = Math.min(TOP_RANK_FOR_REWARD, bests.size());
        for (int i = 0; i < count; i++) {
            UserBestProjection p = bests.get(i);
            String badgeId = resolveBadgeId(i + 1);
            if (badgeId == null || badgeId.isBlank()) {
                continue;
            }
            User user = userRepository.findById(p.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            rewardRepository.save(DailyChallengeReward.create(
                    challenge, user, i + 1, p.getBestScore(), badgeId));
        }
    }

    @Transactional(readOnly = true)
    public List<DailyChallengeReward> findRewardsForChallenge(Long challengeId) {
        return rewardRepository.findByChallenge_IdOrderByRankAtCloseAsc(challengeId);
    }

    @Transactional(readOnly = true)
    public List<DailyChallengeReward> findRewardsForUser(Long userId) {
        return rewardRepository.findByUser_IdOrderByAwardedAtDesc(userId);
    }

    // 등수 → 배지 식별자 매핑. yaml 의 빈 값 / 누락도 안전하게 흡수한다.
    private String resolveBadgeId(int rank) {
        if (badgeConfig == null) {
            return null;
        }
        return switch (rank) {
            case 1 -> badgeConfig.goldId();
            case 2 -> badgeConfig.silverId();
            case 3 -> badgeConfig.bronzeId();
            default -> null;
        };
    }
}

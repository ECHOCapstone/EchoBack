package com.capstoneecho.echo_back.challenge.service;

import com.capstoneecho.echo_back.challenge.dto.ChallengeHistoryItem;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.entity.DailyChallengeReward;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository.UserBestProjection;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRewardRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "지난 챌린지" 페이지의 데이터를 만든다. 본인 best / 순위 / 배지 정보를 한 응답에 묶어 FE 가 추가
// 호출 없이 카드 렌더링을 끝낼 수 있게 한다.
@Service
@Transactional(readOnly = true)
public class ChallengeHistoryService {

    private static final int TOP_PREVIEW_LIMIT = 3;

    private final DailyChallengeRepository challengeRepository;
    private final DailyChallengeAttemptRepository attemptRepository;
    private final DailyChallengeRewardRepository rewardRepository;

    public ChallengeHistoryService(
            DailyChallengeRepository challengeRepository,
            DailyChallengeAttemptRepository attemptRepository,
            DailyChallengeRewardRepository rewardRepository) {
        this.challengeRepository = challengeRepository;
        this.attemptRepository = attemptRepository;
        this.rewardRepository = rewardRepository;
    }

    // 최신 챌린지 N개 (현재 활성 포함). FE 가 "오늘" 외 과거 카드를 시간 역순으로 노출한다.
    public List<ChallengeHistoryItem> recent(Long viewerId, int limit) {
        int safeLimit = Math.max(1, limit);
        List<DailyChallenge> challenges = challengeRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit, Sort.by("createdAt").descending()))
                .getContent();
        List<ChallengeHistoryItem> items = new ArrayList<>(challenges.size());
        for (DailyChallenge challenge : challenges) {
            items.add(buildItem(challenge, viewerId));
        }
        return items;
    }

    private ChallengeHistoryItem buildItem(DailyChallenge challenge, Long viewerId) {
        List<UserBestProjection> bests = attemptRepository.findUserBestsByChallengeId(challenge.getId());
        Map<Integer, String> badgeByRank = buildBadgeByRank(challenge.getId());

        List<ChallengeHistoryItem.TopEntry> top = new ArrayList<>();
        int previewLimit = Math.min(TOP_PREVIEW_LIMIT, bests.size());
        int myRank = 0;
        Double myBest = null;
        String myBadge = null;

        for (int i = 0; i < bests.size(); i++) {
            UserBestProjection p = bests.get(i);
            int rank = i + 1;
            if (i < previewLimit) {
                top.add(new ChallengeHistoryItem.TopEntry(
                        rank, p.getNickname(), p.getBestScore(), badgeByRank.get(rank)));
            }
            if (p.getUserId().equals(viewerId)) {
                myRank = rank;
                myBest = p.getBestScore();
                myBadge = badgeByRank.get(rank);
            }
        }

        return new ChallengeHistoryItem(
                challenge.getId(),
                challenge.getTargetText(),
                challenge.getKoreanTranslation(),
                challenge.getActivatedAt(),
                challenge.getDeactivatedAt(),
                top,
                myBest,
                myRank,
                myBadge);
    }

    // 챌린지의 등수 → 배지 식별자 매핑. reward 가 발급되지 않은 등수는 null.
    private Map<Integer, String> buildBadgeByRank(Long challengeId) {
        Map<Integer, String> map = new HashMap<>();
        for (DailyChallengeReward r : rewardRepository.findByChallenge_IdOrderByRankAtCloseAsc(challengeId)) {
            map.put(r.getRankAtClose(), r.getBadgeId());
        }
        return map;
    }
}

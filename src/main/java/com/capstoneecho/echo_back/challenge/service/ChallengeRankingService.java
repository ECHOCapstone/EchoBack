package com.capstoneecho.echo_back.challenge.service;

import com.capstoneecho.echo_back.challenge.dto.ChallengeRankingResponse;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository.UserBestProjection;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 챌린지의 사용자별 best 점수 랭킹을 만든다. 정렬 / tie-breaker 는 Repository 의 JPQL 이 결정하며,
// 본 클래스는 응답 조립과 본인 메타 (rank / bestScore / attempts / 노출 여부) 만 책임진다.
@Service
@Transactional(readOnly = true)
public class ChallengeRankingService {

    private final DailyChallengeAttemptRepository attemptRepository;
    private final int topN;

    public ChallengeRankingService(
            DailyChallengeAttemptRepository attemptRepository,
            AppProperties appProperties) {
        this.attemptRepository = attemptRepository;
        AppProperties.Challenge challenge = appProperties.challenge();
        this.topN = Math.max(1, challenge == null ? 5 : challenge.topN());
    }

    public ChallengeRankingResponse forChallenge(DailyChallenge challenge, Long viewerId) {
        List<UserBestProjection> bests =
                attemptRepository.findUserBestsByChallengeId(challenge.getId());

        int total = bests.size();
        int limit = Math.min(topN, total);
        List<ChallengeRankingResponse.Entry> topEntries = new ArrayList<>(limit);
        int myRank = 0;
        Double myBest = null;
        long myAttempts = 0L;

        for (int i = 0; i < total; i++) {
            UserBestProjection p = bests.get(i);
            boolean isMe = p.getUserId().equals(viewerId);
            int rank = i + 1;
            if (i < limit) {
                topEntries.add(new ChallengeRankingResponse.Entry(
                        rank, p.getNickname(), p.getBestScore(), p.getAttempts(), isMe));
            }
            if (isMe) {
                myRank = rank;
                myBest = p.getBestScore();
                myAttempts = p.getAttempts();
            }
        }
        boolean myEntryShown = myRank > 0 && myRank <= limit;

        return new ChallengeRankingResponse(
                challenge.getId(),
                challenge.getTargetText(),
                total,
                myRank,
                myBest,
                myAttempts,
                myEntryShown,
                topEntries);
    }
}

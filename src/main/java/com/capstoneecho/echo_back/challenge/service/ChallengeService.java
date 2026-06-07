package com.capstoneecho.echo_back.challenge.service;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 챌린지의 라이프사이클 (생성 / 활성 / 종료) 을 단일 출처로 관리한다.
// "한 번에 active=true 인 챌린지는 하나" 정책을 도메인 차원에서 보장하며, 새 챌린지 활성화 시
// 기존 활성 챌린지가 자동으로 종료되도록 한다. 종료 시점에 등수별 배지 발급은
// ChallengeRewardService 가 책임진다.
@Service
@Transactional
public class ChallengeService {

    private final DailyChallengeRepository challengeRepository;
    private final ChallengeRewardService rewardService;

    public ChallengeService(
            DailyChallengeRepository challengeRepository,
            ChallengeRewardService rewardService) {
        this.challengeRepository = challengeRepository;
        this.rewardService = rewardService;
    }

    // 어드민 등록 진입점. activate=true 면 등록 직후 본 챌린지를 활성으로 띄우고 이전 활성 챌린지를 종료한다.
    public DailyChallenge create(String targetText, String koreanTranslation, boolean activate) {
        DailyChallenge challenge = challengeRepository.save(
                DailyChallenge.create(targetText, koreanTranslation));
        if (activate) {
            activate(challenge.getId());
        }
        return challenge;
    }

    // 챌린지를 활성으로 만든다. 기존 활성 챌린지가 있으면 종료 + 배지 발급 후 본 챌린지를 띄운다.
    public DailyChallenge activate(Long challengeId) {
        DailyChallenge target = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (target.isActive()) {
            return target;
        }
        for (DailyChallenge prev : challengeRepository.findAllByActiveTrue()) {
            closeAndReward(prev);
        }
        target.activate();
        return target;
    }

    // 활성 챌린지를 운영자가 수동으로 종료할 때 호출한다. 종료 시점에 배지 발급도 함께 수행한다.
    public void deactivate(Long challengeId) {
        DailyChallenge target = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        closeAndReward(target);
    }

    // 현재 활성 챌린지를 조회한다. 활성 챌린지가 없으면 빈 Optional.
    @Transactional(readOnly = true)
    public Optional<DailyChallenge> findActive() {
        return challengeRepository.findFirstByActiveTrueOrderByActivatedAtDesc();
    }

    // 활성 챌린지를 강제 요청. 없으면 INACTIVE 예외 — 사용자의 도전 시도에 쓰인다.
    @Transactional(readOnly = true)
    public DailyChallenge requireActive() {
        return findActive().orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_INACTIVE));
    }

    // 어드민 / 사용자 히스토리에서 공용으로 쓰는 목록.
    @Transactional(readOnly = true)
    public List<DailyChallenge> findAllOrderedByCreatedDesc(int limit) {
        return challengeRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit),
                        org.springframework.data.domain.Sort.by("createdAt").descending()))
                .getContent();
    }

    @Transactional(readOnly = true)
    public DailyChallenge requireById(Long id) {
        return challengeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    private void closeAndReward(DailyChallenge challenge) {
        if (!challenge.isActive()) {
            return;
        }
        challenge.deactivate();
        rewardService.awardRanksForChallenge(challenge);
    }
}

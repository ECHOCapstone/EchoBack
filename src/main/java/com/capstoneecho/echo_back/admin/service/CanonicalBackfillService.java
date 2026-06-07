package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalCacheService;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

// 어드민 호출로 canonical_version < 2 인 learning_step / session_sentence / daily_challenge 를 페이지 단위로
// LLM 호출해 갱신한다. 한 번 호출 = 한 페이지 (기본 50건). 페이지를 끝까지 돌리려면 응답의 next 페이지 번호로 재호출.
//
// 호출 자체는 짧은 트랜잭션 안에서 끝내지 않고, 항목별로 CanonicalCacheService 의 REQUIRES_NEW 트랜잭션이
// 따로 커밋한다. 한 항목이 LLM 호출 실패로 던져도 다음 항목으로 계속 진행하며 응답 summary 에 실패 카운트만 누적한다.
@Service
public class CanonicalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(CanonicalBackfillService.class);
    private static final int LEGACY_VERSION = 2;

    private final LearningStepRepository stepRepository;
    private final DailyChallengeRepository challengeRepository;
    private final LlmCanonicalGenerator generator;
    private final CanonicalCacheService cacheService;

    public CanonicalBackfillService(
            LearningStepRepository stepRepository,
            DailyChallengeRepository challengeRepository,
            LlmCanonicalGenerator generator,
            CanonicalCacheService cacheService) {
        this.stepRepository = stepRepository;
        this.challengeRepository = challengeRepository;
        this.generator = generator;
        this.cacheService = cacheService;
    }

    // 한 페이지를 backfill 한다. 응답 = 처리/성공/실패 카운트 + 다음 페이지 번호 (남은 데이터 없으면 null).
    public BackfillResult backfillStepsPage(int page, int size) {
        int pageSize = clampSize(size);
        Page<LearningStep> chunk = stepRepository
                .findAllByKindAndCanonicalVersionLessThanOrderByIdAsc(
                        StepKind.RECORD, LEGACY_VERSION, PageRequest.of(Math.max(0, page), pageSize));
        BackfillTally tally = new BackfillTally();
        for (LearningStep step : chunk.getContent()) {
            try {
                CanonicalResult result = generator.generate(step.getTargetText());
                cacheService.persistStepCache(step.getId(), result);
                tally.success++;
            } catch (BusinessException ex) {
                tally.failure++;
                tally.failures.add(new FailureItem(step.getId(), ex.getMessage()));
                log.warn("step canonical backfill 실패 id={} message={}", step.getId(), ex.getMessage());
            }
            tally.processed++;
        }
        Integer nextPage = chunk.hasNext() ? chunk.getNumber() + 1 : null;
        return new BackfillResult(
                "learning_steps", chunk.getTotalElements(), chunk.getNumber(), pageSize,
                tally.processed, tally.success, tally.failure, tally.failures, nextPage);
    }

    // 챌린지는 데이터 양이 적어 한 번에 처리. 페이지 번호 / size 인자는 호환을 위해 받지만 단일 페이지로 응답한다.
    public BackfillResult backfillChallenges() {
        List<DailyChallenge> all = challengeRepository.findAll().stream()
                .filter(c -> c.getCanonicalVersion() < LEGACY_VERSION)
                .toList();
        BackfillTally tally = new BackfillTally();
        for (DailyChallenge challenge : all) {
            try {
                CanonicalResult result = generator.generate(challenge.getTargetText());
                cacheService.persistChallengeCache(challenge.getId(), result);
                tally.success++;
            } catch (BusinessException ex) {
                tally.failure++;
                tally.failures.add(new FailureItem(challenge.getId(), ex.getMessage()));
                log.warn("challenge canonical backfill 실패 id={} message={}", challenge.getId(), ex.getMessage());
            }
            tally.processed++;
        }
        return new BackfillResult(
                "daily_challenges", all.size(), 0, all.size(),
                tally.processed, tally.success, tally.failure, tally.failures, null);
    }

    private static int clampSize(int size) {
        if (size <= 0) return 50;
        // 한 번에 너무 많이 돌리면 Gemini quota 폭증 + 한 요청 timeout. 운영 안전선으로 200 으로 묶는다.
        return Math.min(200, size);
    }

    private static final class BackfillTally {
        int processed;
        int success;
        int failure;
        final List<FailureItem> failures = new ArrayList<>();
    }

    // 한 페이지 backfill 결과. nextPage 가 null 이면 더 이상 남은 항목이 없다.
    public record BackfillResult(
            String target,
            long totalRemaining,
            int page,
            int pageSize,
            int processed,
            int success,
            int failure,
            List<FailureItem> failures,
            Integer nextPage
    ) {}

    public record FailureItem(Long id, String reason) {}
}

package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 어드민 호출로 canonical_cached_json 이 비어 있는 챌린지를 일괄 채운다.
// learning_steps / session_sentences 는 user-lock 기반이라 backfill 대상이 아니다 (lazy 로 채워짐).
//
// 항목별 LLM 호출이 BusinessException 으로 떨어지면 다음 항목으로 진행하며 응답 summary 에 실패만 누적한다.
@Service
public class CanonicalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(CanonicalBackfillService.class);

    private final DailyChallengeRepository challengeRepository;
    private final LlmCanonicalGenerator generator;
    private final CanonicalJson canonicalJson;

    public CanonicalBackfillService(
            DailyChallengeRepository challengeRepository,
            LlmCanonicalGenerator generator,
            CanonicalJson canonicalJson) {
        this.challengeRepository = challengeRepository;
        this.generator = generator;
        this.canonicalJson = canonicalJson;
    }

    // canonical_cached_json 이 NULL / blank 인 챌린지만 골라 한 번에 처리한다.
    // 항목별 영속화는 별도 트랜잭션으로 분리해 한 건 실패가 전체를 망가뜨리지 않게 한다.
    public BackfillResult backfillChallenges() {
        List<DailyChallenge> all = challengeRepository.findAll().stream()
                .filter(c -> c.getCanonicalCachedJson() == null
                        || c.getCanonicalCachedJson().isBlank())
                .toList();
        BackfillTally tally = new BackfillTally();
        for (DailyChallenge challenge : all) {
            try {
                CanonicalResult result = generator.generate(challenge.getTargetText());
                if (result == null || result.words().isEmpty()) {
                    throw new BusinessException(
                            com.capstoneecho.echo_back.global.common.ErrorCode.CANONICAL_GENERATION_FAILED,
                            "canonical 응답 비어 있음");
                }
                persistOne(challenge.getId(), canonicalJson.serialize(result.words()));
                tally.success++;
            } catch (BusinessException ex) {
                tally.failure++;
                tally.failures.add(new FailureItem(challenge.getId(), ex.getMessage()));
                log.warn("challenge canonical backfill 실패 id={} message={}",
                        challenge.getId(), ex.getMessage());
            }
            tally.processed++;
        }
        return new BackfillResult(
                "daily_challenges", all.size(),
                tally.processed, tally.success, tally.failure, tally.failures);
    }

    // 항목별 영속화는 별도 트랜잭션 — 다음 항목 실패와 분리한다.
    @Transactional
    public void persistOne(Long challengeId, String canonicalJsonBody) {
        DailyChallenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null) {
            return;
        }
        challenge.applyCanonical(canonicalJsonBody);
        challengeRepository.save(challenge);
    }

    private static final class BackfillTally {
        int processed;
        int success;
        int failure;
        final List<FailureItem> failures = new ArrayList<>();
    }

    public record BackfillResult(
            String target,
            long totalRemaining,
            int processed,
            int success,
            int failure,
            List<FailureItem> failures
    ) {}

    public record FailureItem(Long id, String reason) {}
}

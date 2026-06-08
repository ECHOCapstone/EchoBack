package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 어드민 호출로 콘텐츠 (learning_steps / session_sentences / daily_challenges) 의 canonical_cached_json 이
// 비어 있는 행을 일괄로 채운다. 부팅 backfill (CanonicalBootstrapper) 토글이 꺼져 있어도 어드민이
// 강제로 채울 수 있는 경로다.
//
// 한 도메인 안에서 항목별 LLM 호출이 BusinessException 으로 떨어지면 다음 항목으로 진행하며 응답
// summary 에 실패만 누적한다. 항목별 영속화는 별도 트랜잭션으로 분리해 한 건 실패가 페이지 전체를
// 망가뜨리지 않게 한다.
@Service
public class CanonicalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(CanonicalBackfillService.class);

    public enum Target {
        STEPS("learning_steps"),
        SENTENCES("session_sentences"),
        CHALLENGES("daily_challenges");

        private final String label;

        Target(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final LearningStepRepository stepRepository;
    private final SessionSentenceRepository sentenceRepository;
    private final DailyChallengeRepository challengeRepository;
    private final LlmCanonicalGenerator generator;
    private final CanonicalJson canonicalJson;

    public CanonicalBackfillService(
            LearningStepRepository stepRepository,
            SessionSentenceRepository sentenceRepository,
            DailyChallengeRepository challengeRepository,
            LlmCanonicalGenerator generator,
            CanonicalJson canonicalJson) {
        this.stepRepository = stepRepository;
        this.sentenceRepository = sentenceRepository;
        this.challengeRepository = challengeRepository;
        this.generator = generator;
        this.canonicalJson = canonicalJson;
    }

    // 단일 도메인 backfill — 호출자가 명시한 target 만 처리한다.
    public BackfillResult backfill(Target target) {
        return switch (target) {
            case STEPS -> backfillSteps();
            case SENTENCES -> backfillSentences();
            case CHALLENGES -> backfillChallenges();
        };
    }

    // 세 도메인 모두 backfill. 각 도메인 결과를 순서대로 묶어 반환한다.
    public List<BackfillResult> backfillAll() {
        return List.of(backfillSteps(), backfillSentences(), backfillChallenges());
    }

    // RECORD kind 중 canonical_cached_json 이 NULL 인 행만 골라 처리한다. INTRO 는 발화 대상이 아니라 제외.
    public BackfillResult backfillSteps() {
        List<LearningStep> targets = stepRepository.findAll().stream()
                .filter(s -> s.getKind() == StepKind.RECORD)
                .filter(s -> s.getCanonicalCachedJson() == null
                        || s.getCanonicalCachedJson().isBlank())
                .toList();
        BackfillTally tally = new BackfillTally();
        for (LearningStep step : targets) {
            generateAndPersist(
                    step.getId(),
                    step.getTargetText(),
                    json -> persistStep(step.getId(), json),
                    tally,
                    "step");
        }
        return tally.toResult(Target.STEPS.label(), targets.size());
    }

    // canonical_cached_json 이 NULL / blank 인 세션 문장만.
    public BackfillResult backfillSentences() {
        List<SessionSentence> targets = sentenceRepository.findAll().stream()
                .filter(s -> s.getCanonicalCachedJson() == null
                        || s.getCanonicalCachedJson().isBlank())
                .toList();
        BackfillTally tally = new BackfillTally();
        for (SessionSentence sentence : targets) {
            generateAndPersist(
                    sentence.getId(),
                    sentence.getText(),
                    json -> persistSentence(sentence.getId(), json),
                    tally,
                    "sentence");
        }
        return tally.toResult(Target.SENTENCES.label(), targets.size());
    }

    // canonical_cached_json 이 NULL / blank 인 챌린지만.
    public BackfillResult backfillChallenges() {
        List<DailyChallenge> targets = challengeRepository.findAll().stream()
                .filter(c -> c.getCanonicalCachedJson() == null
                        || c.getCanonicalCachedJson().isBlank())
                .toList();
        BackfillTally tally = new BackfillTally();
        for (DailyChallenge challenge : targets) {
            generateAndPersist(
                    challenge.getId(),
                    challenge.getTargetText(),
                    json -> persistChallenge(challenge.getId(), json),
                    tally,
                    "challenge");
        }
        return tally.toResult(Target.CHALLENGES.label(), targets.size());
    }

    // canonical 생성 + 영속화 한 사이클. 한 항목 실패는 다음 항목 진행에 영향을 주지 않는다.
    private void generateAndPersist(
            Long id,
            String text,
            Consumer<String> persist,
            BackfillTally tally,
            String kind) {
        try {
            CanonicalResult result = generator.generate(text);
            if (result == null || result.words().isEmpty()) {
                throw new BusinessException(
                        ErrorCode.CANONICAL_GENERATION_FAILED,
                        "canonical 응답 비어 있음");
            }
            persist.accept(canonicalJson.serialize(result.words()));
            tally.success++;
        } catch (BusinessException ex) {
            tally.failure++;
            tally.failures.add(new FailureItem(id, ex.getMessage()));
            log.warn("{} canonical backfill 실패 id={} message={}", kind, id, ex.getMessage());
        }
        tally.processed++;
    }

    @Transactional
    public void persistStep(Long stepId, String canonicalJsonBody) {
        LearningStep step = stepRepository.findById(stepId).orElse(null);
        if (step == null) {
            return;
        }
        step.applyCanonical(canonicalJsonBody);
        stepRepository.save(step);
    }

    @Transactional
    public void persistSentence(Long sentenceId, String canonicalJsonBody) {
        SessionSentence sentence = sentenceRepository.findById(sentenceId).orElse(null);
        if (sentence == null) {
            return;
        }
        sentence.applyCanonical(canonicalJsonBody);
        sentenceRepository.save(sentence);
    }

    @Transactional
    public void persistChallenge(Long challengeId, String canonicalJsonBody) {
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

        BackfillResult toResult(String label, int total) {
            return new BackfillResult(label, total, processed, success, failure, failures);
        }
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

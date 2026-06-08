package com.capstoneecho.echo_back.external.llm.canonical;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 부팅 시 콘텐츠 테이블의 누락된 canonical 을 비동기 backfill 한다.
// app.canonical.bootstrap-on-startup 토글이 켜진 환경에서만 실행되며 정상 부팅을 막지 않는다.
// 한 페이지 처리 = 한 트랜잭션 — 한 행 실패가 다음 행을 막지 않는다 (canonical_cached_json 가 NULL 인 행만
// 다시 조회하므로 자연스러운 idempotency).
@Component
public class CanonicalBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(CanonicalBootstrapper.class);

    private final LlmCanonicalGenerator generator;
    private final CanonicalJson canonicalJson;
    private final LearningStepRepository stepRepository;
    private final SessionSentenceRepository sentenceRepository;
    private final DailyChallengeRepository challengeRepository;
    private final boolean enabled;
    private final int pageSize;

    public CanonicalBootstrapper(
            LlmCanonicalGenerator generator,
            CanonicalJson canonicalJson,
            LearningStepRepository stepRepository,
            SessionSentenceRepository sentenceRepository,
            DailyChallengeRepository challengeRepository,
            AppProperties appProperties) {
        this.generator = generator;
        this.canonicalJson = canonicalJson;
        this.stepRepository = stepRepository;
        this.sentenceRepository = sentenceRepository;
        this.challengeRepository = challengeRepository;
        AppProperties.Canonical cfg = appProperties.canonical();
        this.enabled = cfg != null && cfg.bootstrapOnStartup();
        int configured = cfg == null ? 50 : cfg.bootstrapPageSize();
        this.pageSize = Math.max(1, Math.min(200, configured));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            return;
        }
        // 별도 스레드 1개로 백그라운드 실행. ApplicationReadyEvent 처리를 막지 않는다.
        Thread runner = Executors.defaultThreadFactory().newThread(this::runAll);
        runner.setName("canonical-bootstrap");
        runner.setDaemon(true);
        runner.start();
    }

    public void runAll() {
        log.info("[canonical bootstrap] start (page size={})", pageSize);
        runStepPages();
        runSentencePages();
        runChallengePages();
        log.info("[canonical bootstrap] done");
    }

    private void runStepPages() {
        int pageIndex = 0;
        while (true) {
            Page<LearningStep> page = stepRepository
                    .findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(
                            StepKind.RECORD, PageRequest.of(0, pageSize));
            if (page.isEmpty()) {
                return;
            }
            log.info("[canonical bootstrap] steps page {} size {} (remaining ~{})",
                    pageIndex, page.getNumberOfElements(), page.getTotalElements());
            int success = persistStepPage(page);
            log.info("[canonical bootstrap] steps page {} done success={} of {}",
                    pageIndex, success, page.getNumberOfElements());
            pageIndex++;
            if (success == 0) {
                // 페이지 안의 모든 행이 실패하면 무한 루프 방지를 위해 종료. 다음 부팅에서 재시도.
                log.warn("[canonical bootstrap] steps page {} all failed — abort", pageIndex);
                return;
            }
        }
    }

    private void runSentencePages() {
        int pageIndex = 0;
        while (true) {
            Page<SessionSentence> page = sentenceRepository
                    .findByCanonicalCachedJsonIsNullOrderByIdAsc(
                            PageRequest.of(0, pageSize));
            if (page.isEmpty()) {
                return;
            }
            log.info("[canonical bootstrap] sentences page {} size {} (remaining ~{})",
                    pageIndex, page.getNumberOfElements(), page.getTotalElements());
            int success = persistSentencePage(page);
            log.info("[canonical bootstrap] sentences page {} done success={} of {}",
                    pageIndex, success, page.getNumberOfElements());
            pageIndex++;
            if (success == 0) {
                log.warn("[canonical bootstrap] sentences page {} all failed — abort", pageIndex);
                return;
            }
        }
    }

    private void runChallengePages() {
        int pageIndex = 0;
        while (true) {
            Page<DailyChallenge> page = challengeRepository
                    .findByCanonicalCachedJsonIsNullOrderByIdAsc(
                            PageRequest.of(0, pageSize));
            if (page.isEmpty()) {
                return;
            }
            log.info("[canonical bootstrap] challenges page {} size {} (remaining ~{})",
                    pageIndex, page.getNumberOfElements(), page.getTotalElements());
            int success = persistChallengePage(page);
            log.info("[canonical bootstrap] challenges page {} done success={} of {}",
                    pageIndex, success, page.getNumberOfElements());
            pageIndex++;
            if (success == 0) {
                log.warn("[canonical bootstrap] challenges page {} all failed — abort", pageIndex);
                return;
            }
        }
    }

    @Transactional
    public int persistStepPage(Page<LearningStep> page) {
        int success = 0;
        for (LearningStep step : page.getContent()) {
            try {
                CanonicalResult result = generator.generate(step.getTargetText());
                String json = canonicalJson.serialize(result.words());
                if (json == null) {
                    continue;
                }
                step.applyCanonical(json);
                stepRepository.save(step);
                success++;
            } catch (RuntimeException ex) {
                log.warn("[canonical bootstrap] step id={} failed: {}", step.getId(), ex.getMessage());
            }
        }
        return success;
    }

    @Transactional
    public int persistSentencePage(Page<SessionSentence> page) {
        int success = 0;
        for (SessionSentence sentence : page.getContent()) {
            try {
                CanonicalResult result = generator.generate(sentence.getText());
                String json = canonicalJson.serialize(result.words());
                if (json == null) {
                    continue;
                }
                sentence.applyCanonical(json);
                sentenceRepository.save(sentence);
                success++;
            } catch (RuntimeException ex) {
                log.warn("[canonical bootstrap] sentence id={} failed: {}",
                        sentence.getId(), ex.getMessage());
            }
        }
        return success;
    }

    @Transactional
    public int persistChallengePage(Page<DailyChallenge> page) {
        int success = 0;
        for (DailyChallenge challenge : page.getContent()) {
            try {
                CanonicalResult result = generator.generate(challenge.getTargetText());
                String json = canonicalJson.serialize(result.words());
                if (json == null) {
                    continue;
                }
                challenge.applyCanonical(json);
                challengeRepository.save(challenge);
                success++;
            } catch (RuntimeException ex) {
                log.warn("[canonical bootstrap] challenge id={} failed: {}",
                        challenge.getId(), ex.getMessage());
            }
        }
        return success;
    }
}

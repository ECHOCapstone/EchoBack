package com.capstoneecho.echo_back.external.llm.canonical;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// 발화 단위 (learning_steps / session_sentences / daily_challenges) 의 canonical JSON 캐시를
// 단일 출처에서 읽고/만들고/저장한다. version<2 이거나 NULL 이면 LlmCanonicalGenerator 로 새로 만든다.
//
// 캐시 쓰기는 호출 트랜잭션과 무관하게 REQUIRES_NEW 로 분리해, attempt 흐름이 HTTP 단계에 있는 동안에는
// DB 커넥션을 잡지 않게 한다. attempt 트랜잭션이 실패해도 캐시는 보존된다 (다음 호출에 재사용).
@Service
public class CanonicalCacheService {

    private final LlmCanonicalGenerator generator;
    private final LearningStepRepository stepRepository;
    private final SessionSentenceRepository sentenceRepository;
    private final DailyChallengeRepository challengeRepository;
    private final ObjectMapper objectMapper;

    public CanonicalCacheService(
            LlmCanonicalGenerator generator,
            LearningStepRepository stepRepository,
            SessionSentenceRepository sentenceRepository,
            DailyChallengeRepository challengeRepository,
            ObjectMapper objectMapper) {
        this.generator = generator;
        this.stepRepository = stepRepository;
        this.sentenceRepository = sentenceRepository;
        this.challengeRepository = challengeRepository;
        this.objectMapper = objectMapper;
    }

    // 한 단계 (record step) 의 canonical 을 가져온다. 캐시 hit 면 즉시 반환, miss 면 LLM 호출 후 저장.
    public CanonicalResult resolveForStep(Long stepId) {
        LearningStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STEP_NOT_FOUND));
        if (isFresh(step.getCanonicalVersion(), step.getCanonicalCachedJson())) {
            return readJson(step.getCanonicalCachedJson());
        }
        CanonicalResult generated = generator.generate(step.getTargetText());
        persistStepCache(stepId, generated);
        return generated;
    }

    // 한 세션 문장의 canonical.
    public CanonicalResult resolveForSentence(Long sentenceId) {
        SessionSentence sentence = sentenceRepository.findById(sentenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_SENTENCE_NOT_FOUND));
        if (isFresh(sentence.getCanonicalVersion(), sentence.getCanonicalCachedJson())) {
            return readJson(sentence.getCanonicalCachedJson());
        }
        CanonicalResult generated = generator.generate(sentence.getText());
        persistSentenceCache(sentenceId, generated);
        return generated;
    }

    // 활성 챌린지 한 문장의 canonical.
    public CanonicalResult resolveForChallenge(Long challengeId) {
        DailyChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (isFresh(challenge.getCanonicalVersion(), challenge.getCanonicalCachedJson())) {
            return readJson(challenge.getCanonicalCachedJson());
        }
        CanonicalResult generated = generator.generate(challenge.getTargetText());
        persistChallengeCache(challengeId, generated);
        return generated;
    }

    // version >= 2 이고 본문이 비어 있지 않으면 즉시 사용 가능.
    private static boolean isFresh(int version, String json) {
        return version >= 2 && json != null && !json.isBlank();
    }

    private CanonicalResult readJson(String json) {
        try {
            CanonicalResult parsed = objectMapper.readValue(json, CanonicalResult.class);
            return parsed == null ? new CanonicalResult(java.util.List.of()) : parsed;
        } catch (JacksonException ex) {
            // 캐시가 파손됐다는 신호 — 사용자에게 명시적 에러를 노출한다.
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "canonical 캐시 파싱 실패");
        }
    }

    private String writeJson(CanonicalResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "canonical 직렬화 실패");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistStepCache(Long stepId, CanonicalResult result) {
        LearningStep step = stepRepository.findById(stepId).orElse(null);
        if (step == null) {
            return;
        }
        step.applyCanonicalCache(writeJson(result));
        stepRepository.save(step);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistSentenceCache(Long sentenceId, CanonicalResult result) {
        SessionSentence sentence = sentenceRepository.findById(sentenceId).orElse(null);
        if (sentence == null) {
            return;
        }
        sentence.applyCanonicalCache(writeJson(result));
        sentenceRepository.save(sentence);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistChallengeCache(Long challengeId, CanonicalResult result) {
        DailyChallenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null) {
            return;
        }
        challenge.applyCanonicalCache(writeJson(result));
        challengeRepository.save(challenge);
    }
}

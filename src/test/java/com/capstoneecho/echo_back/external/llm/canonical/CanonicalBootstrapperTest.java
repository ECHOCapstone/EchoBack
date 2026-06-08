package com.capstoneecho.echo_back.external.llm.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

// CanonicalBootstrapper 의 페이지 단위 backfill 동작 / 실패 행 skip / 빈 페이지 종료 / disabled 시 noop 을 검증한다.
class CanonicalBootstrapperTest {

    private LearningStepRepository stepRepository;
    private SessionSentenceRepository sentenceRepository;
    private DailyChallengeRepository challengeRepository;
    private LlmCanonicalGenerator generator;
    private CanonicalJson canonicalJson;

    @BeforeEach
    void setUp() {
        stepRepository = mock(LearningStepRepository.class);
        sentenceRepository = mock(SessionSentenceRepository.class);
        challengeRepository = mock(DailyChallengeRepository.class);
        generator = mock(LlmCanonicalGenerator.class);
        canonicalJson = new CanonicalJson(new ObjectMapper());
    }

    private CanonicalBootstrapper bootstrapper(boolean enabled) {
        AppProperties.Canonical cfg = new AppProperties.Canonical(enabled, 50);
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                null,
                null, null, null, null, null, null,
                null,
                cfg);
        return new CanonicalBootstrapper(
                generator, canonicalJson, stepRepository, sentenceRepository,
                challengeRepository, props);
    }

    @Test
    @DisplayName("onReady: bootstrap-on-startup=false 면 generator/repository 어느 것도 호출하지 않는다")
    void disabledIsNoop() {
        CanonicalBootstrapper b = bootstrapper(false);
        b.onReady();
        verify(generator, never()).generate(anyString());
        verify(stepRepository, never())
                .findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(any(), any());
    }

    @Test
    @DisplayName("runAll: steps/sentences/challenges 페이지가 모두 비면 generator 호출 없음")
    void allEmptyPagesIsNoop() {
        when(stepRepository.findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(eq(StepKind.RECORD), any()))
                .thenReturn(emptyStepPage());
        when(sentenceRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(emptySentencePage());
        when(challengeRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(emptyChallengePage());

        bootstrapper(true).runAll();

        verify(generator, never()).generate(anyString());
    }

    @Test
    @DisplayName("persistStepPage: 행 한 건 성공 / 한 건 실패 → success=1, 실패 행은 skip")
    void persistStepPagePartialSuccess() {
        Script script = mock(Script.class);
        LearningStep ok = LearningStep.record(script, "say", "the");
        LearningStep bad = LearningStep.record(script, "say", "bad");
        Page<LearningStep> page = new PageImpl<>(List.of(ok, bad));

        when(generator.generate("the")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("the", List.of("DH", "AH")))));
        when(generator.generate("bad")).thenThrow(
                new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "boom"));
        when(stepRepository.save(any(LearningStep.class))).thenAnswer(inv -> inv.getArgument(0));

        int success = bootstrapper(true).persistStepPage(page);

        assertThat(success).isEqualTo(1);
        verify(stepRepository, times(1)).save(any(LearningStep.class));
        assertThat(ok.getCanonicalCachedJson()).isNotNull();
        assertThat(bad.getCanonicalCachedJson()).isNull();
    }

    @Test
    @DisplayName("persistChallengePage: 행 한 건 처리 후 entity 에 canonical JSON 이 채워진다")
    void persistChallengePageWritesCanonical() {
        DailyChallenge challenge = DailyChallenge.create("the", "그");
        Page<DailyChallenge> page = new PageImpl<>(List.of(challenge));

        when(generator.generate("the")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("the", List.of("DH", "IY")))));
        when(challengeRepository.save(any(DailyChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        int success = bootstrapper(true).persistChallengePage(page);

        assertThat(success).isEqualTo(1);
        assertThat(challenge.getCanonicalCachedJson()).contains("the").contains("DH").contains("IY");
    }

    private static Page<LearningStep> emptyStepPage() {
        return new PageImpl<>(List.of(), Pageable.unpaged(), 0);
    }

    private static Page<SessionSentence> emptySentencePage() {
        return new PageImpl<>(List.of(), Pageable.unpaged(), 0);
    }

    private static Page<DailyChallenge> emptyChallengePage() {
        return new PageImpl<>(List.of(), Pageable.unpaged(), 0);
    }
}

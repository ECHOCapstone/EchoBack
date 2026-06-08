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

    @Test
    @DisplayName("runAll: steps 페이지가 1개 처리 후 다음 호출에서 비면 sentences/challenges 로 진행")
    void runAllIteratesAllThreeDomains() {
        Script script = mock(Script.class);
        LearningStep step = LearningStep.record(script, "say", "the");
        // 첫 호출 = 1건 페이지, 두번째 호출 = 빈 페이지 → step pipeline 종료.
        when(stepRepository.findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(eq(StepKind.RECORD), any()))
                .thenReturn(new PageImpl<>(List.of(step)))
                .thenReturn(emptyStepPage());
        SessionSentence sentence = SessionSentenceTestFactory.create("event");
        when(sentenceRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(new PageImpl<>(List.of(sentence)))
                .thenReturn(emptySentencePage());
        DailyChallenge challenge = DailyChallenge.create("hi", "안녕");
        when(challengeRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(new PageImpl<>(List.of(challenge)))
                .thenReturn(emptyChallengePage());

        when(generator.generate(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return new CanonicalResult(List.of(new CanonicalWord(text, List.of("X"))));
        });
        when(stepRepository.save(any(LearningStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sentenceRepository.save(any(SessionSentence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(challengeRepository.save(any(DailyChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        bootstrapper(true).runAll();

        verify(generator, times(1)).generate("the");
        verify(generator, times(1)).generate("event");
        verify(generator, times(1)).generate("hi");
        verify(stepRepository, times(2))
                .findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(eq(StepKind.RECORD), any());
        verify(sentenceRepository, times(2)).findByCanonicalCachedJsonIsNullOrderByIdAsc(any());
        verify(challengeRepository, times(2)).findByCanonicalCachedJsonIsNullOrderByIdAsc(any());
    }

    @Test
    @DisplayName("runAll: steps 첫 페이지가 전부 실패 → 다음 페이지 안 부르고 sentences/challenges 진행")
    void runAllAbortsStepPipelineWhenWholePageFails() {
        Script script = mock(Script.class);
        LearningStep failing = LearningStep.record(script, "say", "boom");
        when(stepRepository.findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(eq(StepKind.RECORD), any()))
                .thenReturn(new PageImpl<>(List.of(failing)));
        when(sentenceRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(emptySentencePage());
        when(challengeRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(emptyChallengePage());

        when(generator.generate("boom")).thenThrow(
                new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "down"));

        bootstrapper(true).runAll();

        // steps 페이지는 1회만 조회된다 (전체 실패 후 abort).
        verify(stepRepository, times(1))
                .findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(eq(StepKind.RECORD), any());
        verify(sentenceRepository, times(1)).findByCanonicalCachedJsonIsNullOrderByIdAsc(any());
        verify(challengeRepository, times(1)).findByCanonicalCachedJsonIsNullOrderByIdAsc(any());
    }

    @Test
    @DisplayName("persistSentencePage: 행 한 건 처리 후 entity 에 canonical JSON 이 채워진다")
    void persistSentencePageWritesCanonical() {
        SessionSentence sentence = SessionSentenceTestFactory.create("hello");
        Page<SessionSentence> page = new PageImpl<>(List.of(sentence));

        when(generator.generate("hello")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("hello", List.of("HH", "AH", "L", "OW")))));
        when(sentenceRepository.save(any(SessionSentence.class))).thenAnswer(inv -> inv.getArgument(0));

        int success = bootstrapper(true).persistSentencePage(page);

        assertThat(success).isEqualTo(1);
        assertThat(sentence.getCanonicalCachedJson()).contains("HH").contains("L");
    }

    @Test
    @DisplayName("persistSentencePage: generator 실패 시 다음 행으로 계속 (success 카운트만 줄어듦)")
    void persistSentencePageSkipsFailures() {
        SessionSentence ok = SessionSentenceTestFactory.create("ok");
        SessionSentence bad = SessionSentenceTestFactory.create("bad");
        Page<SessionSentence> page = new PageImpl<>(List.of(ok, bad));
        when(generator.generate("ok")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("ok", List.of("OW", "K")))));
        when(generator.generate("bad")).thenThrow(
                new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "fail"));
        when(sentenceRepository.save(any(SessionSentence.class))).thenAnswer(inv -> inv.getArgument(0));

        int success = bootstrapper(true).persistSentencePage(page);

        assertThat(success).isEqualTo(1);
        assertThat(ok.getCanonicalCachedJson()).isNotNull();
        assertThat(bad.getCanonicalCachedJson()).isNull();
    }

    @Test
    @DisplayName("persistChallengePage: generator 실패 시 다음 행으로 계속 진행")
    void persistChallengePageSkipsFailures() {
        DailyChallenge ok = DailyChallenge.create("ok", "오케이");
        DailyChallenge bad = DailyChallenge.create("bad", "배드");
        Page<DailyChallenge> page = new PageImpl<>(List.of(bad, ok));
        when(generator.generate("ok")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("ok", List.of("OW", "K")))));
        when(generator.generate("bad")).thenThrow(
                new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "fail"));
        when(challengeRepository.save(any(DailyChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        int success = bootstrapper(true).persistChallengePage(page);

        assertThat(success).isEqualTo(1);
        assertThat(ok.getCanonicalCachedJson()).isNotNull();
        assertThat(bad.getCanonicalCachedJson()).isNull();
    }

    @Test
    @DisplayName("page size 토큰 음수 / 너무 큰 값은 1..200 범위로 clamp 되어 PageRequest 호출이 깨지지 않는다")
    void pageSizeIsClamped() {
        AppProperties.Canonical cfg = new AppProperties.Canonical(true, 999);
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                null,
                null, null, null, null, null, null,
                null,
                cfg);
        CanonicalBootstrapper b = new CanonicalBootstrapper(
                generator, canonicalJson, stepRepository, sentenceRepository,
                challengeRepository, props);
        when(stepRepository.findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(eq(StepKind.RECORD), any()))
                .thenReturn(emptyStepPage());
        when(sentenceRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(emptySentencePage());
        when(challengeRepository.findByCanonicalCachedJsonIsNullOrderByIdAsc(any()))
                .thenReturn(emptyChallengePage());

        b.runAll();
        // 호출이 깨지지 않으면 OK.
    }

    @Test
    @DisplayName("AppProperties.canonical 이 null 이면 기본값 (disabled) — onReady noop")
    void nullCanonicalCfgDisables() {
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                null,
                null, null, null, null, null, null,
                null,
                null);
        CanonicalBootstrapper b = new CanonicalBootstrapper(
                generator, canonicalJson, stepRepository, sentenceRepository,
                challengeRepository, props);

        b.onReady();

        verify(generator, never()).generate(anyString());
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

    // SessionSentence 의 패키지 비공개 factory 를 우회하기 위해 mock 으로 text / canonical 캐시만 흉내낸다.
    // applyCanonical 은 spy 가 아닌 mock 이라 직접 동작하지 않으므로, 호출 시점에 setter 같은 동작을 흉내내도록
    // doAnswer 를 건다.
    private static final class SessionSentenceTestFactory {
        static SessionSentence create(String text) {
            SessionSentence s = mock(SessionSentence.class);
            when(s.getText()).thenReturn(text);
            final String[] holder = new String[1];
            when(s.getCanonicalCachedJson()).thenAnswer(inv -> holder[0]);
            org.mockito.Mockito.doAnswer(inv -> {
                String value = inv.getArgument(0);
                holder[0] = (value == null || value.isBlank()) ? null : value;
                return null;
            }).when(s).applyCanonical(org.mockito.ArgumentMatchers.any());
            return s;
        }
    }
}

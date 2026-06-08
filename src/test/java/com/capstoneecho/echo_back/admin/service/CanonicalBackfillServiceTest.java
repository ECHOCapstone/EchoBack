package com.capstoneecho.echo_back.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

// CanonicalBackfillService 의 도메인 별 backfill 동작, 빈 페이지 / 실패 행 skip / 영속화 흐름을 검증한다.
class CanonicalBackfillServiceTest {

    private LearningStepRepository stepRepository;
    private SessionSentenceRepository sentenceRepository;
    private DailyChallengeRepository challengeRepository;
    private LlmCanonicalGenerator generator;
    private CanonicalJson canonicalJson;
    private CanonicalBackfillService service;

    @BeforeEach
    void setUp() {
        stepRepository = mock(LearningStepRepository.class);
        sentenceRepository = mock(SessionSentenceRepository.class);
        challengeRepository = mock(DailyChallengeRepository.class);
        generator = mock(LlmCanonicalGenerator.class);
        canonicalJson = new CanonicalJson(new ObjectMapper());
        service = new CanonicalBackfillService(
                stepRepository, sentenceRepository, challengeRepository,
                generator, canonicalJson);
    }

    @Test
    @DisplayName("backfill(STEPS): RECORD 중 canonical 비어있는 행만 처리, persistStep 호출")
    void backfillStepsProcessesOnlyMissingRecords() {
        LearningStep recordMissing = stepWithIdAndJson(11L, StepKind.RECORD, "the", null);
        LearningStep recordFilled = stepWithIdAndJson(12L, StepKind.RECORD, "filled", "[{}]");
        LearningStep intro = stepWithIdAndJson(13L, StepKind.INTRO, null, null);
        when(stepRepository.findAll()).thenReturn(List.of(recordMissing, recordFilled, intro));
        when(generator.generate("the")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("the", List.of("DH", "AH")))));
        when(stepRepository.findById(11L)).thenReturn(Optional.of(recordMissing));
        when(stepRepository.save(any(LearningStep.class))).thenAnswer(inv -> inv.getArgument(0));

        CanonicalBackfillService.BackfillResult result = service.backfill(
                CanonicalBackfillService.Target.STEPS);

        assertThat(result.target()).isEqualTo("learning_steps");
        assertThat(result.totalRemaining()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isZero();
        verify(generator, times(1)).generate("the");
        verify(generator, never()).generate("filled");
    }

    @Test
    @DisplayName("backfill(SENTENCES): 빈 응답 → 실패 누적, 다음 항목은 정상 처리")
    void backfillSentencesSkipsBadAndContinues() {
        SessionSentence bad = sentenceWithIdAndJson(21L, "boom", null);
        SessionSentence good = sentenceWithIdAndJson(22L, "ok", null);
        when(sentenceRepository.findAll()).thenReturn(List.of(bad, good));
        when(generator.generate("boom")).thenReturn(new CanonicalResult(List.of()));
        when(generator.generate("ok")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("ok", List.of("OW", "K")))));
        when(sentenceRepository.findById(22L)).thenReturn(Optional.of(good));
        when(sentenceRepository.save(any(SessionSentence.class))).thenAnswer(inv -> inv.getArgument(0));

        CanonicalBackfillService.BackfillResult result = service.backfill(
                CanonicalBackfillService.Target.SENTENCES);

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(21L);
    }

    @Test
    @DisplayName("backfill(CHALLENGES): generator 가 BusinessException → 다음 항목으로 진행")
    void backfillChallengesSwallowsGeneratorException() {
        DailyChallenge bad = challengeWithIdAndJson(31L, "x", null);
        DailyChallenge good = challengeWithIdAndJson(32L, "y", null);
        when(challengeRepository.findAll()).thenReturn(List.of(bad, good));
        when(generator.generate("x")).thenThrow(
                new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "down"));
        when(generator.generate("y")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("y", List.of("W", "AY")))));
        when(challengeRepository.findById(32L)).thenReturn(Optional.of(good));
        when(challengeRepository.save(any(DailyChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        CanonicalBackfillService.BackfillResult result = service.backfillChallenges();

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(1);
        assertThat(good.getCanonicalCachedJson()).contains("W").contains("AY");
    }

    @Test
    @DisplayName("backfill(STEPS): 처리할 행이 없으면 빈 result")
    void backfillStepsEmptyIsNoop() {
        when(stepRepository.findAll()).thenReturn(List.of());

        CanonicalBackfillService.BackfillResult result = service.backfill(
                CanonicalBackfillService.Target.STEPS);

        assertThat(result.totalRemaining()).isZero();
        assertThat(result.processed()).isZero();
        verify(generator, never()).generate(anyString());
    }

    @Test
    @DisplayName("backfillAll: 세 도메인 결과를 순서대로 묶어 돌려준다")
    void backfillAllReturnsAllThree() {
        when(stepRepository.findAll()).thenReturn(List.of());
        when(sentenceRepository.findAll()).thenReturn(List.of());
        when(challengeRepository.findAll()).thenReturn(List.of());

        List<CanonicalBackfillService.BackfillResult> all = service.backfillAll();

        assertThat(all).extracting(CanonicalBackfillService.BackfillResult::target)
                .containsExactly("learning_steps", "session_sentences", "daily_challenges");
    }

    @Test
    @DisplayName("persistStep: 대상 행이 사라졌으면 noop (silent — DB race)")
    void persistStepMissingRowIsNoop() {
        when(stepRepository.findById(99L)).thenReturn(Optional.empty());

        service.persistStep(99L, "[]");

        verify(stepRepository, never()).save(any(LearningStep.class));
    }

    @Test
    @DisplayName("persistSentence: 대상 행이 사라졌으면 noop")
    void persistSentenceMissingRowIsNoop() {
        when(sentenceRepository.findById(99L)).thenReturn(Optional.empty());

        service.persistSentence(99L, "[]");

        verify(sentenceRepository, never()).save(any(SessionSentence.class));
    }

    @Test
    @DisplayName("persistChallenge: 대상 행이 사라졌으면 noop")
    void persistChallengeMissingRowIsNoop() {
        when(challengeRepository.findById(99L)).thenReturn(Optional.empty());

        service.persistChallenge(99L, "[]");

        verify(challengeRepository, never()).save(any(DailyChallenge.class));
    }

    // ---------- mock 엔티티 헬퍼 ----------
    // id 와 getter 만 stub 한 가짜 엔티티. 실제 JPA 영속성은 필요 없다.
    private static LearningStep stepWithIdAndJson(long id, StepKind kind, String text, String json) {
        LearningStep mock = mock(LearningStep.class);
        when(mock.getId()).thenReturn(id);
        when(mock.getKind()).thenReturn(kind);
        when(mock.getTargetText()).thenReturn(text);
        when(mock.getCanonicalCachedJson()).thenReturn(json);
        return mock;
    }

    private static SessionSentence sentenceWithIdAndJson(long id, String text, String json) {
        SessionSentence mock = mock(SessionSentence.class);
        when(mock.getId()).thenReturn(id);
        when(mock.getText()).thenReturn(text);
        when(mock.getCanonicalCachedJson()).thenReturn(json);
        return mock;
    }

    // DailyChallenge 는 Mockito spy 로 ID 만 강제 주입한다. applyCanonical 등 실제 동작은 delegate 가 처리.
    private static DailyChallenge challengeWithIdAndJson(long id, String text, String json) {
        DailyChallenge real = DailyChallenge.create(text, "ko");
        real.applyCanonical(json);
        DailyChallenge spy = org.mockito.Mockito.spy(real);
        when(spy.getId()).thenReturn(id);
        return spy;
    }
}

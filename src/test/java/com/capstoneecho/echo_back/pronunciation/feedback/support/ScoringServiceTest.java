package com.capstoneecho.echo_back.pronunciation.feedback.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// ScoringService 의 결정적 점수 환산 동작을 검증한다.
//   - errors 가 비면 100.
//   - 약점 음소 SUBSTITUTION/DELETION -5.
//   - INSERTION -3.
//   - canonical 길이는 alignment 의 비-INSERTION 항목 수.
//   - 0..100 으로 clamp.
class ScoringServiceTest {

    private static final AppProperties.Scoring DEFAULT_SCORING =
            new AppProperties.Scoring(
                    List.of("V", "R", "L", "TH", "DH", "F", "Z", "ZH", "AH", "AE", "ER"),
                    5, 3, 0);

    private final ScoringService service = newService(DEFAULT_SCORING);

    private static ScoringService newService(AppProperties.Scoring scoring) {
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                null,
                null, null, null, null, null, null,
                scoring,
                null);
        return new ScoringService(props);
    }

    @Test
    @DisplayName("errors 가 비면 100 점")
    void emptyErrorsGivesFullScore() {
        List<AlignmentOp> alignment = List.of(
                match("HH", 0), match("AH", 1), match("L", 2), match("OW", 3));
        assertThat(service.compute(alignment, List.of())).isEqualTo(100);
    }

    @Test
    @DisplayName("canonical 길이가 0 이면 0 점")
    void zeroCanonicalLengthGivesZero() {
        List<LlmPhonemeError> errs = List.of(
                new LlmPhonemeError(AlignmentOp.ErrorType.INSERTION, null, "X", -1));
        assertThat(service.compute(List.of(insertion("X")), errs)).isZero();
    }

    @Test
    @DisplayName("약점 음소 SUBSTITUTION 은 -5 페널티")
    void weakPhonemeSubstitutionDocksFive() {
        List<AlignmentOp> alignment = List.of(
                match("HH", 0), match("AH", 1), match("L", 2),
                new AlignmentOp(AlignmentOp.ErrorType.SUBSTITUTION, "R", "L", 3));
        List<LlmPhonemeError> errs = List.of(
                new LlmPhonemeError(AlignmentOp.ErrorType.SUBSTITUTION, "R", "L", 3));

        int score = service.compute(alignment, errs);

        // base = 3/4 * 100 = 75, penalty = 5 → 70
        assertThat(score).isEqualTo(70);
    }

    @Test
    @DisplayName("INSERTION 은 -3 페널티, canonical 길이 계산에선 무시")
    void insertionDocksThreeAndDoesNotInflateCanonicalLength() {
        List<AlignmentOp> alignment = List.of(
                match("HH", 0), match("AH", 1),
                insertion("X"));
        List<LlmPhonemeError> errs = List.of(
                new LlmPhonemeError(AlignmentOp.ErrorType.INSERTION, null, "X", -1));

        int score = service.compute(alignment, errs);

        // base = 2/2 * 100 = 100, penalty = 3 → 97
        assertThat(score).isEqualTo(97);
    }

    @Test
    @DisplayName("약점 외 음소 SUBSTITUTION 은 베이스 감소만 (페널티 0)")
    void regularPhonemeSubstitutionOnlyAffectsBase() {
        List<AlignmentOp> alignment = List.of(
                match("HH", 0), match("AH", 1),
                new AlignmentOp(AlignmentOp.ErrorType.SUBSTITUTION, "K", "G", 2));
        List<LlmPhonemeError> errs = List.of(
                new LlmPhonemeError(AlignmentOp.ErrorType.SUBSTITUTION, "K", "G", 2));

        int score = service.compute(alignment, errs);

        // base = 2/3 * 100 = 66.6..7 → 67, penalty = 0 → 67
        assertThat(score).isEqualTo(67);
    }

    @Test
    @DisplayName("점수는 0..100 으로 clamp")
    void clampToZero() {
        List<AlignmentOp> alignment = List.of(
                new AlignmentOp(AlignmentOp.ErrorType.SUBSTITUTION, "R", "L", 0),
                new AlignmentOp(AlignmentOp.ErrorType.SUBSTITUTION, "TH", "S", 1),
                new AlignmentOp(AlignmentOp.ErrorType.SUBSTITUTION, "V", "B", 2));
        List<LlmPhonemeError> errs = List.of(
                new LlmPhonemeError(AlignmentOp.ErrorType.SUBSTITUTION, "R", "L", 0),
                new LlmPhonemeError(AlignmentOp.ErrorType.SUBSTITUTION, "TH", "S", 1),
                new LlmPhonemeError(AlignmentOp.ErrorType.SUBSTITUTION, "V", "B", 2));

        int score = service.compute(alignment, errs);

        // base = 0, penalty = 15 → max(0, -15) = 0
        assertThat(score).isZero();
    }

    private static AlignmentOp match(String phoneme, int idx) {
        return new AlignmentOp(AlignmentOp.ErrorType.MATCH, phoneme, phoneme, idx);
    }

    private static AlignmentOp insertion(String perceived) {
        return new AlignmentOp(AlignmentOp.ErrorType.INSERTION, null, perceived, -1);
    }
}

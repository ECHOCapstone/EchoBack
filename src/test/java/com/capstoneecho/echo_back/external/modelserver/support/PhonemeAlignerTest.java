package com.capstoneecho.echo_back.external.modelserver.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// PhonemeAligner 가 op 시퀀스를 만드는 정렬 동작 자체를 검증한다. PER 가중치 정책은 별도
// WeightedPerCalculatorTest 에서 검증하므로, 본 테스트는 uniform 정책 (가중치 모두 1.0) 을 주입해
// 편집거리 / canonical 길이 라는 단순 PER 의미가 유지되게 한다.
class PhonemeAlignerTest {

    private final PhonemeAligner aligner = new PhonemeAligner(uniformWeightedPerCalculator());

    @Test
    @DisplayName("ts 분해 후 정렬: results 발음 시 첫 음소만 substitution, 나머지는 정확 매칭")
    void alignsResultsAfterTsDecomposition() {
        List<String> canonical = List.of("IH", "Z", "AH", "L", "T", "S");
        // 모델이 [IY, Z, AH, L, TS] 를 출력해 normalizer 가 [IY, Z, AH, L, t, s] 로 분해한 상태
        List<String> perceived = List.of("IY", "Z", "AH", "L", "t", "s");

        PhonemeAligner.AlignmentResult result = aligner.align(canonical, perceived);

        assertThat(result.errors()).hasSize(1);
        AnalyzeError sub = result.errors().get(0);
        assertThat(sub.op()).isEqualTo("substitution");
        assertThat(sub.canonical()).isEqualTo("IH");
        assertThat(sub.perceived()).isEqualTo("IY");
        assertThat(sub.canonicalIndex()).isZero();
        assertThat(result.per()).isCloseTo(1.0 / 6.0, within(1e-9));
    }

    @Test
    @DisplayName("perceived 가 canonical 보다 짧으면 부족분이 deletion 으로 잡힌다")
    void detectsDeletionForMissingTrailingPhonemes() {
        List<String> canonical = List.of("HH", "AH", "L", "OW");
        List<String> perceived = List.of("HH", "AH");

        PhonemeAligner.AlignmentResult result = aligner.align(canonical, perceived);

        assertThat(result.errors()).extracting(AnalyzeError::op)
                .containsExactly("deletion", "deletion");
        assertThat(result.errors().get(0).canonical()).isEqualTo("L");
        assertThat(result.errors().get(0).canonicalIndex()).isEqualTo(2);
        assertThat(result.errors().get(1).canonical()).isEqualTo("OW");
        assertThat(result.errors().get(1).canonicalIndex()).isEqualTo(3);
        assertThat(result.per()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("perceived 가 canonical 보다 길면 초과분이 insertion 으로 잡힌다")
    void detectsInsertionForExtraPhonemes() {
        List<String> canonical = List.of("L", "AY", "T");
        // light 인데 모델이 hh iy l ay k t 로 인식 → 추가 hh, iy, k 가 insertion
        List<String> perceived = List.of("HH", "IY", "L", "AY", "K", "T");

        PhonemeAligner.AlignmentResult result = aligner.align(canonical, perceived);

        assertThat(result.errors()).extracting(AnalyzeError::op)
                .containsOnly("insertion");
        assertThat(result.errors()).extracting(AnalyzeError::perceived)
                .containsExactlyInAnyOrder("HH", "IY", "K");
        // uniform 정책에서 PER = 편집거리 / canonical 길이 = 3 / 3 = 1.0
        assertThat(result.per()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("canonical 이 비면 perceived 전체가 insertion 으로 잡히고 per 는 0.0 으로 안전 처리된다")
    void handlesEmptyCanonical() {
        PhonemeAligner.AlignmentResult result = aligner.align(List.of(), List.of("AA"));

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).op()).isEqualTo("insertion");
        assertThat(result.per()).isZero();
    }

    @Test
    @DisplayName("대소문자가 달라도 같은 음소로 인정된다")
    void caseInsensitiveMatch() {
        PhonemeAligner.AlignmentResult result = aligner.align(List.of("HH", "AH"), List.of("hh", "ah"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.per()).isZero();
    }

    // weak 음소 가중치와 insertion 가중치를 모두 1.0 으로 둔 uniform 정책. 본 테스트는 정렬 로직 자체만
    // 검증하므로, WeightedPerCalculator 의 가중 정책이 PER 기댓값에 섞이지 않도록 분리한다.
    private static WeightedPerCalculator uniformWeightedPerCalculator() {
        AppProperties.Scoring scoring = new AppProperties.Scoring(
                List.of(), 1.0, 1.0, 1.0);
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                scoring,
                null,
                null, null, null, null, null, null);
        return new WeightedPerCalculator(props);
    }

    private static org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}

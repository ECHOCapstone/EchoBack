package com.capstoneecho.echo_back.pronunciation.feedback.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// ScoringService 의 길이 정규화 + 자질거리 가중 점수 환산을 검증한다.
//   score = round(100 × (1 − Σ오류가중치 / canonical 길이))
//   SUB = subW × max(floor, 자질거리) × (약점이면 weakMult), DEL = delW × (약점이면 weakMult), INS = insW.
class ScoringServiceTest {

    private static final AppProperties.Scoring DEFAULT_SCORING =
            new AppProperties.Scoring(
                    List.of("V", "R", "L", "TH", "DH", "F", "Z", "ZH", "AH", "AE", "ER"),
                    1.5,   // weakMultiplier
                    1.0,   // substitutionWeight
                    1.0,   // deletionWeight
                    0.5,   // insertionWeight
                    0.3);  // distanceFloor

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
    @DisplayName("전 항목 MATCH 면 100 점")
    void allMatchGivesFullScore() {
        List<AlignmentOp> alignment = List.of(
                match("HH", 0), match("AH", 1), match("L", 2), match("OW", 3));
        assertThat(service.compute(alignment)).isEqualTo(100);
    }

    @Test
    @DisplayName("정렬이 비면 0 점")
    void emptyAlignmentGivesZero() {
        assertThat(service.compute(List.of())).isZero();
    }

    @Test
    @DisplayName("canonical 길이가 0(삽입만) 이면 0 점")
    void zeroCanonicalLengthGivesZero() {
        assertThat(service.compute(List.of(insertion("X")))).isZero();
    }

    @Test
    @DisplayName("약점 음소 치환(R→L): floor 0.3 × weakMult 1.5 = 0.45 → 4길이에서 89점")
    void weakSubstitutionUsesFloorTimesWeakMultiplier() {
        List<AlignmentOp> alignment = List.of(
                match("HH", 0), match("AH", 1), match("L", 2), sub("R", "L", 3));
        // 100 × (1 − 0.45/4) = 88.75 → 89
        assertThat(service.compute(alignment)).isEqualTo(89);
    }

    @Test
    @DisplayName("INSERTION 은 insertionWeight 0.5, canonical 길이엔 안 들어간다")
    void insertionUsesInsertionWeight() {
        List<AlignmentOp> alignment = List.of(match("HH", 0), match("AH", 1), insertion("X"));
        // 100 × (1 − 0.5/2) = 75
        assertThat(service.compute(alignment)).isEqualTo(75);
    }

    @Test
    @DisplayName("자질거리 변별: 가까운 치환(AE→EH)이 먼 치환(AE→K)보다 높은 점수")
    void closerSubstitutionScoresHigher() {
        List<AlignmentOp> close = List.of(
                match("HH", 0), match("N", 1), match("D", 2), sub("AE", "EH", 3));
        List<AlignmentOp> far = List.of(
                match("HH", 0), match("N", 1), match("D", 2), sub("AE", "K", 3));

        int closeScore = service.compute(close);
        int farScore = service.compute(far);

        // close: floor 0.3 × 1.5 = 0.45 → 89,  far: 1.0 × 1.5 = 1.5 → 63
        assertThat(closeScore).isGreaterThan(farScore);
        assertThat(closeScore).isEqualTo(89);
        assertThat(farScore).isEqualTo(63);
    }

    @Test
    @DisplayName("약점 외 자음 치환(K→G, 유성성 차이)은 자질거리만큼만 깎인다")
    void regularSubstitutionUsesFeatureDistance() {
        List<AlignmentOp> alignment = List.of(match("HH", 0), match("AH", 1), sub("K", "G", 2));
        // K→G 거리 = (1 + 0 + 0)/3 = 0.333 → 100 × (1 − 0.333/3) = 88.9 → 89
        assertThat(service.compute(alignment)).isEqualTo(89);
    }

    @Test
    @DisplayName("점수는 0 으로 clamp")
    void clampToZero() {
        // 먼 치환 둘(자음↔모음, 약점) → 오류질량이 길이를 넘어 음수 → 0
        List<AlignmentOp> alignment = List.of(sub("R", "AE", 0), sub("AE", "K", 1));
        assertThat(service.compute(alignment)).isZero();
    }

    private static AlignmentOp match(String phoneme, int idx) {
        return new AlignmentOp(AlignmentOp.ErrorType.MATCH, phoneme, phoneme, idx);
    }

    private static AlignmentOp sub(String canonical, String perceived, int idx) {
        return new AlignmentOp(AlignmentOp.ErrorType.SUBSTITUTION, canonical, perceived, idx);
    }

    private static AlignmentOp insertion(String perceived) {
        return new AlignmentOp(AlignmentOp.ErrorType.INSERTION, null, perceived, -1);
    }
}

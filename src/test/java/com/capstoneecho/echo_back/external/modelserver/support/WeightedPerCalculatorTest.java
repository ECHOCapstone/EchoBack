package com.capstoneecho.echo_back.external.modelserver.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// app.scoring 정책의 가중 PER 계산기. 한국인 학습자의 약점 음소와 insertion 가중이 어떻게 점수에
// 반영되는지 시나리오 단위로 명시한다. 가중치 / 임계값을 튜닝할 때 본 테스트의 시나리오를 기준으로
// "정상 발음은 PER X 이하, 약점 발음은 PER Y 이상" 같은 정책 의도를 회귀 방지한다.
class WeightedPerCalculatorTest {

    // 기본값과 동일한 정책. 시나리오 테스트가 yaml 디폴트와 같은 출처를 공유하도록 한 곳에서 만든다.
    private static WeightedPerCalculator calculator(
            List<String> weakPhonemes,
            double weakMultiplier,
            double insertionWeight) {
        AppProperties.Scoring scoring = new AppProperties.Scoring(
                weakPhonemes, weakMultiplier, insertionWeight, 1.0);
        AppProperties props = appProperties(scoring);
        return new WeightedPerCalculator(props);
    }

    // AppProperties 의 다른 필드는 본 테스트와 무관하므로 null 로 둔다 — Scoring 외 필드는 참조되지 않는다.
    private static AppProperties appProperties(AppProperties.Scoring scoring) {
        return new AppProperties(
                null, null, null, null, null, null, null, null,
                scoring,
                null, null, null, null, null);
    }

    private static AnalyzeError sub(String canonical, String perceived) {
        return new AnalyzeError("substitution", 0, perceived, canonical);
    }

    private static AnalyzeError del(String canonical) {
        return new AnalyzeError("deletion", 0, null, canonical);
    }

    private static AnalyzeError ins(String perceived) {
        return new AnalyzeError("insertion", null, perceived, null);
    }

    @Nested
    @DisplayName("디폴트 정책 (weak ×1.5, insertion 0.7)")
    class DefaultPolicy {

        private final WeightedPerCalculator calc = calculator(
                List.of("V", "R", "TH", "AH"), 1.5, 0.7);

        @Test
        @DisplayName("정상 발음 — 오류 없음 → PER 0.0")
        void noErrors() {
            double per = calc.computeWeightedPer(List.of(), 5);
            assertThat(per).isEqualTo(0.0);
        }

        @Test
        @DisplayName("약점 음소 substitution — V→B 한 번이 일반 음소 1 회보다 가중치 ↑")
        void weakPhonemeSubstitutionWeightsMore() {
            // canonical 길이 = 4 (very 의 V EH R IY)
            double weakSub = calc.computeWeightedPer(List.of(sub("V", "B")), 4);
            // 같은 위치에 일반 음소(M)의 substitution 이 발생했을 때
            double normalSub = calc.computeWeightedPer(List.of(sub("M", "N")), 4);

            // 약점 음소 가중 1.5 → 1.5 / 4 = 0.375
            assertThat(weakSub).isCloseTo(0.375, within(1e-6));
            // 일반 음소 1.0 → 1.0 / 4 = 0.25
            assertThat(normalSub).isCloseTo(0.25, within(1e-6));
            // 약점 PER 가 일반 PER 보다 명확히 크다 — 약점이 점수에 더 강하게 반영됨.
            assertThat(weakSub).isGreaterThan(normalSub);
        }

        @Test
        @DisplayName("insertion 가중치 — 'globarrr' 의 추가 r 들이 PER 에 0.7 씩 누적")
        void insertionContributesPartial() {
            // canonical 길이 = 6 (global: G L OW B AH L), insertion 3 회 (R R R)
            double per = calc.computeWeightedPer(
                    List.of(ins("R"), ins("R"), ins("R")), 6);
            // 0.7 × 3 / 6 = 0.35
            assertThat(per).isCloseTo(0.35, within(1e-6));
        }

        @Test
        @DisplayName("약점 음소 deletion 도 weak multiplier 적용 — 한국인이 마지막 음소를 빠뜨려도 더 무겁게 잡힘")
        void weakPhonemeDeletion() {
            // canonical 길이 = 3 (TH 가 weak)
            double per = calc.computeWeightedPer(List.of(del("TH")), 3);
            // 1.5 / 3 = 0.5
            assertThat(per).isCloseTo(0.5, within(1e-6));
        }

        @Test
        @DisplayName("canonical 이 비어 있으면 PER 0.0 — 분모가 없으므로 안전 처리")
        void emptyCanonicalReturnsZero() {
            double per = calc.computeWeightedPer(List.of(sub("V", "B")), 0);
            assertThat(per).isEqualTo(0.0);
        }

        @Test
        @DisplayName("PER 상한 1.0 클램프 — 짧은 정답에 오류가 폭증해도 점수 음수로 떨어지지 않게")
        void capsAtOne() {
            // canonical 길이 1 인데 5 개 오류면 weighted sum = 5 → 분모 1 로 PER 5.0 이지만 1.0 으로 클램프.
            double per = calc.computeWeightedPer(
                    List.of(sub("M", "N"), sub("M", "N"), sub("M", "N"),
                            sub("M", "N"), sub("M", "N")), 1);
            assertThat(per).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("정책 튜닝 시나리오 — 시연 직전 미니 테스트")
    class TuningScenarios {

        @Test
        @DisplayName("V/R/TH 약점 multiplier 를 2.0 으로 올리면 같은 오류의 PER 가 더 커진다")
        void higherMultiplierIncreasesPenalty() {
            WeightedPerCalculator soft = calculator(List.of("V"), 1.5, 0.7);
            WeightedPerCalculator strict = calculator(List.of("V"), 2.0, 0.7);

            double softPer = soft.computeWeightedPer(List.of(sub("V", "B")), 4);
            double strictPer = strict.computeWeightedPer(List.of(sub("V", "B")), 4);

            assertThat(strictPer).isGreaterThan(softPer);
        }

        @Test
        @DisplayName("weak 음소 목록이 비어 있으면 모든 sub/del 가중치가 1.0 으로 동일")
        void emptyWeakListMeansUniformWeight() {
            WeightedPerCalculator uniform = calculator(List.of(), 1.5, 0.7);

            double weakLook = uniform.computeWeightedPer(List.of(sub("V", "B")), 4);
            double normal = uniform.computeWeightedPer(List.of(sub("M", "N")), 4);

            assertThat(weakLook).isCloseTo(normal, within(1e-6));
        }

        @Test
        @DisplayName("insertion-weight 0 으로 두면 추가 음소가 점수에 전혀 영향 없음")
        void insertionWeightZeroIgnoresInsertions() {
            WeightedPerCalculator noIns = calculator(List.of(), 1.5, 0.0);

            double per = noIns.computeWeightedPer(
                    List.of(ins("R"), ins("R"), ins("R")), 6);
            assertThat(per).isEqualTo(0.0);
        }

        @Test
        @DisplayName("음수 / 과대 가중치는 클램프로 흡수 — yaml 오타 안전망")
        void clampsBadConfig() {
            WeightedPerCalculator negative = calculator(List.of("V"), 0.5, -1.0);

            // weak multiplier 는 1.0 미만이면 1.0 으로, insertion 은 0 미만이면 0.0 으로.
            assertThat(negative.weakPhonemeMultiplier()).isEqualTo(1.0);
            assertThat(negative.insertionWeight()).isEqualTo(0.0);
        }
    }
}

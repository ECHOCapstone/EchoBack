package com.capstoneecho.echo_back.pronunciation.feedback.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "한 번 더 연습할 약점 음소" 추천 정책. errors 의 첫 항목을 그대로 노출하면 영어 문장 첫 부분의 슈와(AH) 가
// 매번 같은 자리에서 잡혀 추천이 단조로워지므로, 빈도 + weak 가중 + 슈와 페널티의 합성 점수로 고른다.
class WeakPhonemeAnalyzerTest {

    private static WeakPhonemeAnalyzer analyzer(List<String> weakPhonemes) {
        AppProperties.Scoring scoring = new AppProperties.Scoring(weakPhonemes, 1.5, 0.7, 1.0);
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                scoring,
                null,
                null, null, null, null, null, null);
        return new WeakPhonemeAnalyzer(props);
    }

    private static AnalyzeError sub(String canonical) {
        return new AnalyzeError("substitution", 0, "x", canonical);
    }

    @Test
    @DisplayName("슈와(AH) 만 잡힌 시도는 정직하게 AH 를 노출한다")
    void schwaOnlyKeepsSchwa() {
        WeakPhonemeAnalyzer w = analyzer(List.of("V", "R", "TH", "AH"));
        String pick = w.firstCanonical(List.of(sub("AH"), sub("AH")));
        assertThat(pick).isEqualTo("AH");
    }

    @Test
    @DisplayName("AH 와 V(weak) 가 함께 잡히면 V 가 우선 — 슈와 페널티 + weak 가산")
    void weakConsonantBeatsSchwa() {
        WeakPhonemeAnalyzer w = analyzer(List.of("V", "R", "TH", "AH"));
        // 빈도: AH 2, V 1 → 점수: AH 2 × 1.5 × 0.5 = 1.5 (schwaPenalty + weakBonus), V 1 × 1.5 = 1.5.
        // 동률이므로 첫 등장한 AH 가 선택되지 않도록 — 점수가 같으면 update 안 한다 (>). 시나리오 보강:
        // AH 1, V 1 → AH 1 × 1.5 × 0.5 = 0.75, V 1 × 1.5 = 1.5 → V 가 명확히 우세.
        String pick = w.firstCanonical(List.of(sub("AH"), sub("V")));
        assertThat(pick).isEqualTo("V");
    }

    @Test
    @DisplayName("AH 와 일반 음소가 함께 잡히면 일반 음소가 우선 (슈와 페널티만 적용)")
    void regularPhonemeBeatsSchwa() {
        WeakPhonemeAnalyzer w = analyzer(List.of("V", "R", "TH", "AH"));
        // AH 1 × 1.5 × 0.5 = 0.75, IH 1 × 1.0 = 1.0 → IH 우세.
        String pick = w.firstCanonical(List.of(sub("AH"), sub("IH")));
        assertThat(pick).isEqualTo("IH");
    }

    @Test
    @DisplayName("일반 음소만 잡히면 빈도가 가장 높은 음소를 고른다")
    void plainFrequencyForNormalPhonemes() {
        WeakPhonemeAnalyzer w = analyzer(List.of("V", "R"));
        String pick = w.firstCanonical(List.of(sub("M"), sub("N"), sub("N"), sub("N")));
        assertThat(pick).isEqualTo("N");
    }

    @Test
    @DisplayName("weak 음소가 빈도 1, 일반 음소가 빈도 2 면 점수가 같을 때는 weak 가 먼저 등장한 쪽 유지")
    void weakBonusTiesWithFrequency() {
        WeakPhonemeAnalyzer w = analyzer(List.of("V"));
        // V 1 × 1.5 = 1.5, M 2 × 1.0 = 2.0 → M 우세 (단순 빈도 2 가 weak 가산을 이김).
        String pick = w.firstCanonical(List.of(sub("V"), sub("M"), sub("M")));
        assertThat(pick).isEqualTo("M");
    }

    @Test
    @DisplayName("errors 가 비면 null 을 반환한다")
    void emptyErrorsReturnNull() {
        WeakPhonemeAnalyzer w = analyzer(List.of("V"));
        assertThat(w.firstCanonical(List.of())).isNull();
        assertThat(w.firstCanonical(null)).isNull();
    }

    @Test
    @DisplayName("대소문자가 달라도 같은 음소로 카운트한다")
    void caseInsensitive() {
        WeakPhonemeAnalyzer w = analyzer(List.of("V"));
        String pick = w.firstCanonical(List.of(sub("v"), sub("V")));
        assertThat(pick).isEqualTo("V");
    }
}

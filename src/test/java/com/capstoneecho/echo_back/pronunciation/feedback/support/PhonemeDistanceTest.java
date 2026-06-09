package com.capstoneecho.echo_back.pronunciation.feedback.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 음소 자질 거리(0~1) 검증.
class PhonemeDistanceTest {

    @Test
    @DisplayName("같은 음소는 0")
    void identityIsZero() {
        assertThat(PhonemeDistance.distance("R", "R")).isZero();
        assertThat(PhonemeDistance.distance("ae", "AE")).isZero(); // 대소문자 무관
    }

    @Test
    @DisplayName("모음↔자음은 최대 거리 1.0")
    void vowelVsConsonantIsMax() {
        assertThat(PhonemeDistance.distance("AE", "K")).isEqualTo(1.0);
        assertThat(PhonemeDistance.distance("R", "IY")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("미상 음소는 안전하게 1.0")
    void unknownIsMax() {
        assertThat(PhonemeDistance.distance("XQ", "K")).isEqualTo(1.0);
        assertThat(PhonemeDistance.distance(null, "K")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("유성성만 다른 자음(T/D)은 작은 양수 거리")
    void voicingOnlyIsSmall() {
        double d = PhonemeDistance.distance("T", "D");
        assertThat(d).isGreaterThan(0.0).isLessThan(0.5);
    }

    @Test
    @DisplayName("가까운 모음 치환(AE/EH)이 먼 치환(AE/UW)보다 작다")
    void closerVowelPairIsSmaller() {
        double near = PhonemeDistance.distance("AE", "EH");   // 높이만 차이
        double farVowel = PhonemeDistance.distance("AE", "UW"); // 높이+전후+원순 차이
        assertThat(near).isLessThan(farVowel);
    }

    @Test
    @DisplayName("R/L 은 자질상 매우 가깝다(거의 0) — 약점 가중은 ScoringService 책임")
    void rAndLAreClose() {
        assertThat(PhonemeDistance.distance("R", "L")).isLessThan(0.1);
    }
}

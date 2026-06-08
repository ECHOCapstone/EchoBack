package com.capstoneecho.echo_back.external.modelserver.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhonemeNormalizerTest {

    private final PhonemeNormalizer normalizer = new PhonemeNormalizer();

    @Test
    @DisplayName("ts 는 T, S 두 표준 음소로 분해되고 peak_softmax 는 같은 값으로 복제된다")
    void decomposesTsAndDuplicatesSoftmax() {
        List<String> phonemes = List.of("IY", "Z", "AH", "L", "TS");
        List<Double> softmax = List.of(0.9, 0.85, 0.82, 0.8, 0.77);

        PhonemeNormalizer.NormalizedPhonemes result = normalizer.normalize(phonemes, softmax);

        assertThat(result.phonemes()).containsExactly("IY", "Z", "AH", "L", "T", "S");
        assertThat(result.peakSoftmax()).containsExactly(0.9, 0.85, 0.82, 0.8, 0.77, 0.77);
    }

    @Test
    @DisplayName("dz 도 D, Z 두 음소로 분해된다 (대소문자 / 공백 흡수)")
    void decomposesDzCaseInsensitive() {
        List<String> phonemes = List.of(" Dz ");

        PhonemeNormalizer.NormalizedPhonemes result = normalizer.normalize(phonemes, List.of(0.7));

        assertThat(result.phonemes()).containsExactly("D", "Z");
        assertThat(result.peakSoftmax()).containsExactly(0.7, 0.7);
    }

    @Test
    @DisplayName("모델이 소문자로 줘도 대문자 ARPABET 으로 정규화해 canonical 과 케이스가 맞는다")
    void uppercasesPerceivedPhonemes() {
        List<String> phonemes = List.of("r", "ay", "t");

        PhonemeNormalizer.NormalizedPhonemes result = normalizer.normalize(phonemes, List.of(0.9, 0.8, 0.7));

        assertThat(result.phonemes()).containsExactly("R", "AY", "T");
        assertThat(result.peakSoftmax()).containsExactly(0.9, 0.8, 0.7);
    }

    @Test
    @DisplayName("peak_softmax 가 null 이면 결과의 softmax 도 빈 리스트로 정규화한다")
    void handlesNullSoftmax() {
        List<String> phonemes = List.of("TS");

        PhonemeNormalizer.NormalizedPhonemes result = normalizer.normalize(phonemes, null);

        assertThat(result.phonemes()).containsExactly("T", "S");
        assertThat(result.peakSoftmax()).isEmpty();
    }
}

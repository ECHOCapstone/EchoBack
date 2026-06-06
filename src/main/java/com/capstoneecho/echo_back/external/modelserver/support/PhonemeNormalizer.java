package com.capstoneecho.echo_back.external.modelserver.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

// 모델 서버가 단일 토큰으로 출력하지만 표준 ARPABET 정답은 둘 이상의 음소로 나뉘는 경우를
// 정답과 같은 표준 음소들로 분해한다. 예: "ts" (results, cats) → ["t", "s"].
// 분해 시 peak_softmax 도 같은 값으로 복제해 인덱스 정합성을 유지한다 — 이후 alignment 재계산이
// perceived 와 peak_softmax 를 같은 길이로 참조한다.
@Component
public class PhonemeNormalizer {

    // 분해 대상 매핑. 키는 소문자 입력 기준. 새 비표준 음소는 여기에 추가하면 즉시 적용된다.
    private static final Map<String, List<String>> DECOMPOSITIONS = Map.of(
            "ts", List.of("t", "s"),
            "dz", List.of("d", "z")
    );

    public NormalizedPhonemes normalize(List<String> phonemes, List<Double> peakSoftmax) {
        if (phonemes == null || phonemes.isEmpty()) {
            return new NormalizedPhonemes(List.of(), peakSoftmax == null ? List.of() : List.copyOf(peakSoftmax));
        }
        boolean hasSoftmax = peakSoftmax != null && !peakSoftmax.isEmpty();
        List<String> outPhonemes = new ArrayList<>(phonemes.size());
        List<Double> outSoftmax = hasSoftmax ? new ArrayList<>(peakSoftmax.size()) : null;

        for (int i = 0; i < phonemes.size(); i++) {
            String original = phonemes.get(i);
            String key = original == null ? "" : original.trim().toLowerCase(Locale.ROOT);
            List<String> decomposed = DECOMPOSITIONS.get(key);
            Double softmaxValue = (hasSoftmax && i < peakSoftmax.size()) ? peakSoftmax.get(i) : null;

            if (decomposed == null) {
                outPhonemes.add(original);
                if (outSoftmax != null) {
                    outSoftmax.add(softmaxValue);
                }
            } else {
                // 분해된 각 표준 음소에 같은 peak_softmax 값을 복제한다 — 모델 신뢰도는 원래 한 토큰에 부여된 값이라
                // 분해 후에도 그대로 이어진다.
                outPhonemes.addAll(decomposed);
                if (outSoftmax != null) {
                    for (int j = 0; j < decomposed.size(); j++) {
                        outSoftmax.add(softmaxValue);
                    }
                }
            }
        }
        return new NormalizedPhonemes(
                outPhonemes,
                outSoftmax == null ? List.of() : outSoftmax);
    }

    public record NormalizedPhonemes(List<String> phonemes, List<Double> peakSoftmax) {}
}

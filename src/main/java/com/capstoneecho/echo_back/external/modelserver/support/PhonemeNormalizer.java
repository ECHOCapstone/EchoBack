package com.capstoneecho.echo_back.external.modelserver.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

// 모델 서버의 인식 음소를 표준 ARPABET 형태로 정규화한다.
//   1) 단일 토큰이지만 표준 정답은 둘 이상으로 나뉘는 경우 분해한다. 예: "ts" (results, cats) → ["T", "S"].
//      분해 시 peak_softmax 도 같은 값으로 복제해 인덱스 정합성을 유지한다 — 이후 alignment 가
//      perceived 와 peak_softmax 를 같은 길이로 참조한다.
//   2) 대문자로 통일한다. ARPABET 은 대문자가 표준이고 canonical 도 대문자라, 모델이 소문자로 줘도
//      정렬·채점·표시에서 R 과 r 이 다른 음소로 취급되지 않게 한다.
@Component
public class PhonemeNormalizer {

    // 분해 대상 매핑. 키는 소문자 입력 기준. 새 비표준 음소는 여기에 추가하면 즉시 적용된다.
    private static final Map<String, List<String>> DECOMPOSITIONS = Map.of(
            "ts", List.of("t", "s"),
            "dz", List.of("d", "z")
    );

    public NormalizedPhonemes normalize(List<String> phonemes, List<Double> peakSoftmax) {
        if (phonemes == null || phonemes.isEmpty()) {
            // List.copyOf 는 null 원소가 있으면 NPE. peakSoftmax 는 외부 응답이라 신뢰하지 않고
            // 일반 ArrayList 로 안전 복사한다 — null 원소가 섞여 있어도 무해히 통과한다.
            return new NormalizedPhonemes(
                    List.of(),
                    peakSoftmax == null ? List.of() : new ArrayList<>(peakSoftmax));
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
                outPhonemes.add(canonicalize(original));
                if (outSoftmax != null) {
                    outSoftmax.add(softmaxValue);
                }
            } else {
                // 분해된 각 표준 음소에 같은 peak_softmax 값을 복제한다 — 모델 신뢰도는 원래 한 토큰에 부여된 값이라
                // 분해 후에도 그대로 이어진다.
                for (String part : decomposed) {
                    outPhonemes.add(canonicalize(part));
                    if (outSoftmax != null) {
                        outSoftmax.add(softmaxValue);
                    }
                }
            }
        }
        return new NormalizedPhonemes(
                outPhonemes,
                outSoftmax == null ? List.of() : outSoftmax);
    }

    // 표준 ARPABET 케이스(대문자)로 맞춘다. null 은 그대로 둬 상위의 null 처리 규칙을 깨지 않는다.
    private static String canonicalize(String phoneme) {
        return phoneme == null ? null : phoneme.trim().toUpperCase(Locale.ROOT);
    }

    public record NormalizedPhonemes(List<String> phonemes, List<Double> peakSoftmax) {}
}

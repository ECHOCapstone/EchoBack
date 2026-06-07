package com.capstoneecho.echo_back.external.modelserver.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 정렬 결과 (substitution / deletion / insertion) 로부터 가중 PER 를 계산한다.
//
// 정책 (단일 출처: app.scoring):
//   - 약점 음소 (weak-phonemes) 의 sub / del 에는 weak-phoneme-multiplier 가중을 적용해
//     한국인 학습자의 흔한 약점 (V/R/TH 등) 이 점수에 더 강하게 반영되도록 한다.
//   - insertion 은 insertion-weight 가중 — 학습자가 추가로 끼워 넣은 음소 ("globarrr") 의
//     기여를 별도 조절해 채점 친화성을 정한다.
//   - canonical 길이가 0 이거나 errors 가 비면 PER 0.0 (정렬 자체가 의미 없을 때 무피드백 회귀 방지).
@Component
public class WeightedPerCalculator {

    private static final double MAX_PER = 1.0;

    private final Set<String> weakPhonemes;
    private final double weakPhonemeMultiplier;
    private final double insertionWeight;

    public WeightedPerCalculator(AppProperties appProperties) {
        AppProperties.Scoring scoring = appProperties.scoring();
        this.weakPhonemes = scoring.safeWeakPhonemes().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        // 약점 가중치는 1.0 미만으로 떨어지면 "약점일수록 덜 깎인다" 라는 정책 위반이라 1.0 으로 클램프한다.
        this.weakPhonemeMultiplier = Math.max(1.0, scoring.weakPhonemeMultiplier());
        // insertion 가중치는 0~2 구간으로 제한 — 음수 / 과대값은 정책 의도가 아닌 설정 오타에 가깝다.
        this.insertionWeight = clamp(scoring.insertionWeight(), 0.0, 2.0);
    }

    // errors 시퀀스를 PER 한 값으로 환산한다.
    //   sub / del : 1.0 (canonical 음소가 weak 집합에 있으면 multiplier 가중)
    //   insertion : insertionWeight 고정
    //   분모       : canonicalSize (음소 시퀀스 길이) — perceived 길이는 PhonemeAligner 가 정렬 시 흡수.
    public double computeWeightedPer(List<AnalyzeError> errors, int canonicalSize) {
        if (canonicalSize <= 0 || errors == null || errors.isEmpty()) {
            return 0.0;
        }
        double weightedSum = 0.0;
        for (AnalyzeError e : errors) {
            if (e == null) continue;
            String op = e.op();
            if (op == null) continue;
            if ("insertion".equals(op)) {
                weightedSum += insertionWeight;
                continue;
            }
            if ("substitution".equals(op) || "deletion".equals(op)) {
                weightedSum += isWeak(e.canonical()) ? weakPhonemeMultiplier : 1.0;
            }
            // op 가 "correct" 면 가중치 추가 없음 — errors 에는 통상 비-correct 만 들어오지만 방어적 처리.
        }
        return Math.min(MAX_PER, weightedSum / canonicalSize);
    }

    // 채점 정책을 노출해 ScoringPolicy 가 perToScore 비선형 변환에서 같은 출처를 참조할 수 있게 한다.
    public Set<String> weakPhonemes() {
        return weakPhonemes;
    }

    public double weakPhonemeMultiplier() {
        return weakPhonemeMultiplier;
    }

    public double insertionWeight() {
        return insertionWeight;
    }

    private boolean isWeak(String phoneme) {
        if (phoneme == null) {
            return false;
        }
        return weakPhonemes.contains(phoneme.trim().toUpperCase(Locale.ROOT));
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }
}

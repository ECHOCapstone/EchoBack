package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 사용자에게 노출할 "한 번 더 연습할 약점 음소" 를 고른다. 단순 빈도가 아니라 학습 가치를 가산한다.
//
// 정책:
//   1) 빈도 점수 = canonical 음소 등장 횟수.
//   2) weak phoneme (V/R/TH 등 한국인 흔한 약점) 이면 가중 ×1.5 — 다른 일반 음소보다 학습 가치 ↑.
//   3) 슈와(AH) 는 영어 무강세 모음 자리에 광범위하게 등장해 "어떤 단어를 연습할지" 가이드가 모호하다.
//      다른 음소가 있을 때만 ×0.5 로 후순위 — 다만 AH 만 잡혔다면 그대로 추천한다.
@Component
public class WeakPhonemeAnalyzer {

    private static final double WEAK_BONUS = 1.5;
    private static final double SCHWA_PENALTY = 0.5;
    private static final String SCHWA = "AH";

    private final Set<String> weakPhonemes;

    public WeakPhonemeAnalyzer(AppProperties appProperties) {
        AppProperties.Scoring scoring = appProperties.scoring();
        this.weakPhonemes = (scoring == null ? List.<String>of() : scoring.safeWeakPhonemes())
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    // 챕터 종합 — 여러 시도의 모든 오류 음소를 누적 집계.
    public String topOneFromErrors(List<AnalyzeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return pickDominant(List.of(errors));
    }

    // 단일 시도 — 본 메서드 결과가 LLM 프롬프트의 `weakPhoneme` 와 phonemeTips 추천에 영향을 준다.
    // 이름은 호환을 위해 유지하되, 의미는 "이 시도의 대표 약점 음소" 다 (단순 첫 항목 X).
    public String firstCanonical(List<AnalyzeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return pickDominant(List.of(errors));
    }

    // 빈도 카운트 + weak 가산 + 슈와 페널티 의 합산 점수 최댓값을 고른다.
    private String pickDominant(Collection<? extends Collection<AnalyzeError>> errorBatches) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Collection<AnalyzeError> batch : errorBatches) {
            if (batch == null) {
                continue;
            }
            for (AnalyzeError error : batch) {
                String key = normalize(error.canonical());
                if (key == null) {
                    continue;
                }
                counts.merge(key, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return null;
        }
        boolean schwaOnly = counts.size() == 1 && counts.containsKey(SCHWA);

        String top = null;
        double topScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String phoneme = entry.getKey();
            double score = entry.getValue();
            if (weakPhonemes.contains(phoneme)) {
                score *= WEAK_BONUS;
            }
            // 다른 음소가 함께 있을 때만 슈와를 후순위로 — 슈와만 잡힌 시도는 정직하게 슈와를 노출한다.
            if (SCHWA.equals(phoneme) && !schwaOnly) {
                score *= SCHWA_PENALTY;
            }
            if (score > topScore) {
                topScore = score;
                top = phoneme;
            }
        }
        return top;
    }

    private static String normalize(String phoneme) {
        if (phoneme == null) {
            return null;
        }
        String trimmed = phoneme.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}

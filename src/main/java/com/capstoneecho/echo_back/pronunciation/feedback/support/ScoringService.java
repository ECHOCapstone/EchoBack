package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

// 결정적 정렬(PhonemeAligner) → 0~100 정수 점수의 결정적 환산. 길이 정규화 + 자질거리 가중.
//   score = round( 100 × (1 − Σ오류가중치 / canonical길이) ), 0..100 clamp
//   오류가중치:
//     SUBSTITUTION = substitutionWeight × max(distanceFloor, 자질거리(정답,인식)) × (약점이면 weakMultiplier)
//     DELETION     = deletionWeight × (약점이면 weakMultiplier)
//     INSERTION    = insertionWeight
//   canonical 길이 = alignment 중 INSERTION 이 아닌 항목 수.
//
// 절대 페널티가 아니라 길이로 정규화하므로, 같은 종류 오류가 발화 길이와 무관하게 일정 비율로 깎인다.
// 치환은 자질거리로 심각도를 변별하고(가까운 치환은 작게, 먼 치환은 크게), 한국인 약점 음소는
// 자질상 가까워도 weakMultiplier 로 가중한다.
//
// 오류는 alignment 의 비-MATCH 항목에서 직접 파생한다 — 화면 표시 정렬은 LLM 이 따로 만들지만 점수는
// 이 결정적 정렬에서만 나온다. 정책 값은 AppProperties.Scoring (yaml SSOT) 에서 로드한다.
@Service
public class ScoringService {

    private final Set<String> weakPhonemes;
    private final double weakMultiplier;
    private final double substitutionWeight;
    private final double deletionWeight;
    private final double insertionWeight;
    private final double distanceFloor;

    public ScoringService(AppProperties appProperties) {
        AppProperties.Scoring scoring = appProperties.scoring();
        List<String> configured = scoring == null ? List.of() : scoring.safeWeakPhonemes();
        Set<String> upper = new HashSet<>(configured.size());
        for (String p : configured) {
            if (p == null) {
                continue;
            }
            String norm = p.trim().toUpperCase(Locale.ROOT);
            if (!norm.isEmpty()) {
                upper.add(norm);
            }
        }
        this.weakPhonemes = Set.copyOf(upper);
        this.weakMultiplier = scoring == null ? 1.5 : scoring.weakMultiplier();
        this.substitutionWeight = scoring == null ? 1.0 : scoring.substitutionWeight();
        this.deletionWeight = scoring == null ? 1.0 : scoring.deletionWeight();
        this.insertionWeight = scoring == null ? 0.5 : scoring.insertionWeight();
        this.distanceFloor = scoring == null ? 0.3 : scoring.distanceFloor();
    }

    public int compute(List<AlignmentOp> alignment) {
        if (alignment == null || alignment.isEmpty()) {
            return 0;
        }
        long canonicalLen = alignment.stream()
                .filter(op -> op.errorType() != AlignmentOp.ErrorType.INSERTION)
                .count();
        if (canonicalLen == 0) {
            return 0;
        }
        double errorMass = 0.0;
        for (AlignmentOp op : alignment) {
            switch (op.errorType()) {
                case MATCH -> { /* 오류 아님 */ }
                case INSERTION -> errorMass += insertionWeight;
                case DELETION -> errorMass += deletionWeight * weakFactor(op.canonical());
                case SUBSTITUTION -> {
                    double dist = Math.max(distanceFloor, PhonemeDistance.distance(op.canonical(), op.perceived()));
                    errorMass += substitutionWeight * dist * weakFactor(op.canonical());
                }
            }
        }
        int raw = (int) Math.round(100.0 * (1.0 - errorMass / canonicalLen));
        return Math.max(0, Math.min(100, raw));
    }

    private double weakFactor(String canonical) {
        return isWeak(canonical) ? weakMultiplier : 1.0;
    }

    public boolean isWeak(String phoneme) {
        if (phoneme == null || phoneme.isBlank()) {
            return false;
        }
        return weakPhonemes.contains(phoneme.trim().toUpperCase(Locale.ROOT));
    }
}

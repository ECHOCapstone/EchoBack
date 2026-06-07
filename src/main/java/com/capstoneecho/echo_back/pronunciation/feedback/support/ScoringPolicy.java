package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

// 점수 계산을 한 곳에서 결정한다. PER (phoneme error rate) → 0~100 스코어 환산이 단일 출처.
@Component
public class ScoringPolicy {

    private static final double PERFECT_SCORE = 100.0;
    private static final double ZERO_SCORE = 0.0;

    private final RuntimeSettings settings;

    public ScoringPolicy(RuntimeSettings settings) {
        this.settings = settings;
    }

    // 여러 녹음 점수의 평균. 점수가 한 건도 없으면 만점으로 본다.
    public double aggregate(Collection<Recording> recordings) {
        if (recordings == null || recordings.isEmpty()) {
            return PERFECT_SCORE;
        }
        double sum = 0.0;
        int count = 0;
        for (Recording r : recordings) {
            Double score = r.getStepScore();
            if (score != null) {
                sum += clamp(score);
                count++;
            }
        }
        if (count == 0) {
            return PERFECT_SCORE;
        }
        return sum / count;
    }

    // 단어 단위 점수: AnalyzeResult 가 있으면 op 별 가중 채점, 없으면 fallback.
    public double singleWordScore(AnalyzeResult analyze) {
        if (analyze == null) {
            return PERFECT_SCORE;
        }
        if (analyze.per() == null) {
            List<AnalyzeError> errors = analyze.errors();
            return (errors == null || errors.isEmpty()) ? PERFECT_SCORE : settings.scoreFallbackOnError();
        }
        return scoreFromAnalyze(analyze);
    }

    // 표준 학습 채점. errors 의 op 별 가중치를 적용하고 분모를 정규화한다.
    //   substitution / deletion : 가중치 1.0 — 정답 음소를 잘못 발음했거나 빠뜨림.
    //   insertion                : 가중치 < 1.0 (기본 0.5) — 추가 음소가 끼어든 경우. 학습자 친화로 약화.
    //   denominator              : max(N_canonical, N_perceived) — perceived 가 더 길어도 per 가 1 을 안 넘게.
    public double scoreFromAnalyze(AnalyzeResult analyze) {
        if (analyze == null) {
            return PERFECT_SCORE;
        }
        List<AnalyzeError> errors = analyze.errors();
        // errors 가 비어 있으면 per 기반으로 폴백한다. 두 케이스를 같이 처리한다:
        //   1) 모든 음소가 일치한 정상 케이스 (per=0 → 100점).
        //   2) 외부 응답이 per 만 보내고 op 별 errors 는 비운 시나리오 (모델 / 테스트 호환).
        // errors 가 있으면 그 안의 op-weighted 합산이 더 정확한 출처이므로 그쪽을 우선한다.
        if (errors == null || errors.isEmpty()) {
            return analyze.per() == null ? PERFECT_SCORE : perToScore(analyze.per());
        }
        int canonicalLen = analyze.canonicalOrEmpty().size();
        int perceivedLen = analyze.perceived() == null ? 0 : analyze.perceived().size();
        int denominator = Math.max(canonicalLen, perceivedLen);
        if (denominator == 0) {
            return PERFECT_SCORE;
        }
        double weightedErrors = 0.0;
        double insertionWeight = settings.scoringInsertionWeight();
        for (AnalyzeError e : errors) {
            String op = e.op();
            if (op == null) continue;
            if ("insertion".equals(op)) {
                weightedErrors += insertionWeight;
            } else if ("substitution".equals(op) || "deletion".equals(op)) {
                weightedErrors += 1.0;
            }
        }
        double adjustedPer = Math.min(1.0, weightedErrors / denominator);
        return clamp((1.0 - adjustedPer) * PERFECT_SCORE);
    }

    // 호환용 — analyze 객체 없이 PER 한 값만 환산. 새 호출자는 scoreFromAnalyze 사용을 권장.
    public double perToScore(double per) {
        return clamp((1.0 - per) * PERFECT_SCORE);
    }

    private static double clamp(double v) {
        if (v < ZERO_SCORE) return ZERO_SCORE;
        if (v > PERFECT_SCORE) return PERFECT_SCORE;
        return v;
    }
}

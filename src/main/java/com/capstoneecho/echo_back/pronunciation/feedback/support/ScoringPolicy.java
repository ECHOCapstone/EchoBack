package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.Collection;
import org.springframework.stereotype.Component;

// 점수 계산을 한 곳에서 결정한다. PER (phoneme error rate) → 0~100 스코어 환산이 단일 출처.
// 가중 PER (약점 음소 / insertion 가중치) 자체는 WeightedPerCalculator 가 책임지고, 본 클래스는
// 그 결과를 받아 비선형 (perToScoreExponent) 변환과 fallback / clamp 정책만 담당한다.
@Component
public class ScoringPolicy {

    private static final double PERFECT_SCORE = 100.0;
    private static final double ZERO_SCORE = 0.0;
    private static final double MIN_EXPONENT = 1.0;
    private static final double MAX_EXPONENT = 3.0;

    private final RuntimeSettings settings;
    private final double perToScoreExponent;

    public ScoringPolicy(RuntimeSettings settings, AppProperties appProperties) {
        this.settings = settings;
        this.perToScoreExponent = clampExponent(appProperties.scoring().perToScoreExponent());
    }

    // 여러 녹음 점수의 평균. 점수가 한 건도 없으면 0 점으로 본다 — 모델 서버 장애 등으로 단 한 건도
    // 채점되지 않은 상태에서 만점을 돌려주면 학습자가 거짓 "완벽" 으로 인식하는 회귀가 생긴다.
    public double aggregate(Collection<Recording> recordings) {
        if (recordings == null || recordings.isEmpty()) {
            return ZERO_SCORE;
        }
        double sum = 0.0;
        int count = 0;
        for (Recording r : recordings) {
            Double score = r.getStepScore();
            if (score != null) {
                sum += clampScore(score);
                count++;
            }
        }
        if (count == 0) {
            return ZERO_SCORE;
        }
        return sum / count;
    }

    // 단어 단위 점수: AnalyzeResult 가 있으면 op 별 가중 채점, 분석 신호가 비면 fallback 점수를 사용한다.
    // analyze 가 null 이거나 per 가 비어 있을 때 만점을 돌려주면 분석 장애가 통과로 위장된다.
    public double singleWordScore(AnalyzeResult analyze) {
        if (analyze == null || analyze.per() == null) {
            return settings.scoreFallbackOnError();
        }
        return scoreFromAnalyze(analyze);
    }

    // 표준 학습 채점. 정렬 결과의 가중 PER (WeightedPerCalculator 가 약점 음소 / insertion 가중치를
    // 이미 반영한 값) 를 비선형 변환해 0~100 점수로 환산한다. analyze 자체가 없거나 per 가 비면
    // "분석이 완료되지 않음" 으로 보고 fallback 점수를 돌려준다 — 만점을 주면 장애가 통과로 위장된다.
    public double scoreFromAnalyze(AnalyzeResult analyze) {
        if (analyze == null || analyze.per() == null) {
            return settings.scoreFallbackOnError();
        }
        return perToScore(analyze.per());
    }

    // PER → 점수 변환. exponent 가 1.0 이면 선형 (1 - per) × 100, 그 이상이면 작은 오류도 더 가파르게 깎인다.
    public double perToScore(double per) {
        double clampedPer = Math.max(0.0, Math.min(1.0, per));
        double survival = Math.pow(1.0 - clampedPer, perToScoreExponent);
        return clampScore(survival * PERFECT_SCORE);
    }

    private static double clampScore(double v) {
        if (v < ZERO_SCORE) return ZERO_SCORE;
        if (v > PERFECT_SCORE) return PERFECT_SCORE;
        return v;
    }

    // 비선형 지수의 안전 범위. 1.0 미만이면 "오류가 적을수록 더 깎임" 으로 정책이 뒤집히고,
    // 너무 크면 0점이 너무 쉽게 나와 학습 동기를 꺾는다. yaml 오타 / NaN 도 디폴트로 복귀시킨다.
    private static double clampExponent(double v) {
        if (Double.isNaN(v) || v < MIN_EXPONENT) return MIN_EXPONENT;
        if (v > MAX_EXPONENT) return MAX_EXPONENT;
        return v;
    }
}

package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

// 점수 계산을 한 곳에서 결정한다. PER (phoneme error rate) → 0~100 스코어 환산이 단일 출처.
@Component
public class ScoringPolicy {

    private static final double PERFECT_SCORE = 100.0;
    private static final double ZERO_SCORE = 0.0;

    // PER 가 비어 있을 때 사용하는 폴백 점수. app.gamification.score-fallback-on-error 로 외부화.
    private final double scoreFallbackOnError;

    public ScoringPolicy(AppProperties appProperties) {
        AppProperties.Gamification g = appProperties.gamification();
        this.scoreFallbackOnError = g == null ? 70.0 : g.scoreFallbackOnError();
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

    // 단어 단위 점수: PER 가 있으면 (1-PER)*100, 없으면 에러 유무로 100 / fallback 분기.
    public double singleWordScore(AnalyzeResult analyze) {
        if (analyze == null) {
            return PERFECT_SCORE;
        }
        Double per = analyze.per();
        if (per == null) {
            List<AnalyzeError> errors = analyze.errors();
            return (errors == null || errors.isEmpty()) ? PERFECT_SCORE : scoreFallbackOnError;
        }
        return perToScore(per);
    }

    // PER 한 값을 0~100 스코어로 정규화한다. 호출처 (RecordingService, singleWordScore) 모두가 사용.
    public double perToScore(double per) {
        return clamp((1.0 - per) * PERFECT_SCORE);
    }

    private static double clamp(double v) {
        if (v < ZERO_SCORE) return ZERO_SCORE;
        if (v > PERFECT_SCORE) return PERFECT_SCORE;
        return v;
    }
}

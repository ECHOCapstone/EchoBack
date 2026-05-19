package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.feedback.dto.ModelAnalyzeResponse;
import org.springframework.stereotype.Component;

import java.util.List;

// 점수 계산의 단일 지점. score = alpha * accuracyFromPer + (1 - alpha) * meanPeak.
// per 또는 peak 가 비어 있는 입력도 안전하게 0~100 범위의 값을 반환한다.
@Component
public class ScoringPolicy {

    private final double alpha;

    public ScoringPolicy(AppProperties properties) {
        this.alpha = properties.scoring().alpha();
    }

    public double scoreOf(ModelAnalyzeResponse response) {
        double accuracy = accuracyFromPer(response.per());
        double meanPeak = meanPeak(response.peakSoftmax());
        return clamp01(alpha * accuracy + (1.0 - alpha) * meanPeak) * 100.0;
    }

    public double averageScore(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (var s : scores) {
            sum += s;
        }
        return sum / scores.size();
    }

    private double accuracyFromPer(Double per) {
        if (per == null) {
            return 1.0;
        }
        return clamp01(1.0 - per);
    }

    private double meanPeak(List<Double> peaks) {
        if (peaks == null || peaks.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (var p : peaks) {
            sum += p;
        }
        return clamp01(sum / peaks.size());
    }

    private double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}

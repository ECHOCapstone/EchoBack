package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScoringPolicy {

    public double aggregate(Collection<Recording> recordings) {
        if (recordings == null || recordings.isEmpty()) {
            return 100.0;
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
            return 100.0;
        }
        return sum / count;
    }

    public double singleWordScore(AnalyzeResult analyze) {
        if (analyze == null) {
            return 100.0;
        }
        Double per = analyze.per().orElse(null);
        if (per == null) {
            List<AnalyzeError> errors = analyze.errors();
            return (errors == null || errors.isEmpty()) ? 100.0 : 70.0;
        }
        double score = (1.0 - per) * 100.0;
        return clamp(score);
    }

    private static double clamp(double v) {
        if (v < 0.0) return 0.0;
        if (v > 100.0) return 100.0;
        return v;
    }
}

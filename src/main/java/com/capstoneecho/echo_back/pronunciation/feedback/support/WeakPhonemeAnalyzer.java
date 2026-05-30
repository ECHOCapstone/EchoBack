package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

// 여러 시도의 음소 오류를 모아 가장 자주 틀린 canonical 음소 하나를 고른다.
@Component
public class WeakPhonemeAnalyzer {

    public String topOneFromErrors(List<AnalyzeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return topOne(List.of(errors));
    }

    private String topOne(Collection<? extends Collection<AnalyzeError>> errorBatches) {
        if (errorBatches == null || errorBatches.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Collection<AnalyzeError> batch : errorBatches) {
            if (batch == null) {
                continue;
            }
            for (AnalyzeError error : batch) {
                String key = error.canonical();
                if (key == null || key.isBlank()) {
                    continue;
                }
                counts.merge(key, 1, Integer::sum);
            }
        }
        String top = null;
        int topCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > topCount) {
                top = entry.getKey();
                topCount = entry.getValue();
            }
        }
        return top;
    }
}

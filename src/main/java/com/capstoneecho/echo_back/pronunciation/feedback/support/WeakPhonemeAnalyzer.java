package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.pronunciation.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.capstoneecho.echo_back.pronunciation.feedback.service.FeedbackServiceImpl;
// 한 회차의 녹음들을 모아 가장 자주 틀린 음소(약점) 와 누적 오류 목록을 뽑아낸다.
// FeedbackServiceImpl 의 흐름 조립과 정책(빈도 집계) 을 떼어 두기 위한 컴포넌트.
@Component
public class WeakPhonemeAnalyzer {

    private final PhonemeErrorMapper errorMapper;

    public WeakPhonemeAnalyzer(PhonemeErrorMapper errorMapper) {
        this.errorMapper = errorMapper;
    }

    public Result analyze(List<Recording> recordings) {
        var aggregated = new ArrayList<PhonemeErrorResponse>();
        for (var r : recordings) {
            aggregated.addAll(errorMapper.deserialize(r.getErrorsJson()));
        }
        return new Result(aggregated, pickWeakest(aggregated));
    }

    // canonical 이 있으면 그것을, 없으면 perceived 를 키로 빈도를 센다. 가장 많이 등장한 음소가 약점.
    private static String pickWeakest(List<PhonemeErrorResponse> errors) {
        if (errors.isEmpty()) return null;
        Map<String, Integer> count = new HashMap<>();
        for (var e : errors) {
            var key = e.canonical() != null ? e.canonical() : e.perceived();
            if (key == null) continue;
            count.merge(key, 1, Integer::sum);
        }
        return count.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public record Result(List<PhonemeErrorResponse> errors, String weakPhoneme) {}
}

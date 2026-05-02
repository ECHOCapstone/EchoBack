package com.capstoneecho.echo_back.app.recording.dto;

import com.capstoneecho.echo_back.app.recording.Recording;

import java.time.Instant;
import java.util.List;

// 한 단계 녹음 업로드의 즉시 응답. perceived/canonical/peakSoftmax 는 모델 분석 결과,
// stepScore 는 ScoringPolicy 에 따른 0~100 점수, guidanceKr 은 채팅 흐름에 노출되는 한 줄 가이드.
public record RecordingResponse(
        Long id,
        Long scriptId,
        Long sessionId,
        Long stepId,
        Double durationSec,
        List<String> perceived,
        List<String> canonical,
        List<Double> peakSoftmax,
        Double stepScore,
        String guidanceKr,
        Instant createdAt
) {

    public static RecordingResponse from(Recording r) {
        return new RecordingResponse(
                r.getId(),
                r.getScriptId(),
                r.getSessionId(),
                r.getStepId(),
                r.getDurationSec(),
                splitPhonemes(r.getPerceived()),
                splitPhonemes(r.getCanonical()),
                splitDoubles(r.getPeakSoftmax()),
                r.getStepScore(),
                r.getGuidanceKr(),
                r.getCreatedAt()
        );
    }

    private static List<String> splitPhonemes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return List.of(raw.trim().split("\\s+"));
    }

    private static List<Double> splitDoubles(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        var parts = raw.trim().split("\\s+");
        var out = new java.util.ArrayList<Double>(parts.length);
        for (var p : parts) {
            try {
                out.add(Double.parseDouble(p));
            } catch (NumberFormatException ignored) {
                // 손상된 값은 무시한다
            }
        }
        return out;
    }
}

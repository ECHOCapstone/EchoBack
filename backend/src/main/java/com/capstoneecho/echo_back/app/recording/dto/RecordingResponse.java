package com.capstoneecho.echo_back.app.recording.dto;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.recording.Recording;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 한 단계 녹음 업로드의 즉시 응답. perceived/canonical/peakSoftmax 는 모델 분석 결과,
// stepScore 는 ScoringPolicy 에 따른 0~100 점수, guidanceKr 은 채팅 흐름에 노출되는 한 줄 가이드.
// errors 는 Levenshtein 정렬 결과 중 틀린 음소 항목 (op = substitution / insertion / deletion).
// wrongWords 는 LLM 이 잘못 발음했다고 판정한 영어 단어 목록. 프론트는 이걸로 targetText 의
// 해당 단어들을 빨강 처리한다. LLM 이 비활성화돼 있으면 빈 배열.
public record RecordingResponse(
        Long id,
        Long scriptId,
        Long sessionId,
        Long stepId,
        Long sessionSentenceId,
        Double durationSec,
        List<String> perceived,
        List<String> canonical,
        List<Double> peakSoftmax,
        Double stepScore,
        String guidanceKr,
        List<PhonemeErrorResponse> errors,
        List<String> wrongWords,
        Instant createdAt
) {

    public static RecordingResponse from(Recording r, List<PhonemeErrorResponse> errors, List<String> wrongWords) {
        return new RecordingResponse(
                r.getId(),
                r.getScriptId(),
                r.getSessionId(),
                r.getStepId(),
                r.getSessionSentenceId(),
                r.getDurationSec(),
                splitPhonemes(r.getPerceived()),
                splitPhonemes(r.getCanonical()),
                splitDoubles(r.getPeakSoftmax()),
                r.getStepScore(),
                r.getGuidanceKr(),
                errors != null ? errors : List.of(),
                wrongWords != null ? wrongWords : List.of(),
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
        var out = new ArrayList<Double>(parts.length);
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

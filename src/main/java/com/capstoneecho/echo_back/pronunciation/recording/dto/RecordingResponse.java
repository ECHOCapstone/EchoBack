package com.capstoneecho.echo_back.pronunciation.recording.dto;

import com.capstoneecho.echo_back.pronunciation.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.WrongWord;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
// 한 단계 녹음 업로드의 즉시 응답. perceived/canonical/peakSoftmax 는 모델 분석 결과,
// stepScore 는 ScoringPolicy 에 따른 0~100 점수, guidanceKr 은 채팅 흐름에 노출되는 한 줄 가이드.
// errors 는 Levenshtein 정렬 결과 중 틀린 음소 항목 (op = substitution / insertion / deletion).
// wrongWords 는 LLM 이 짚어 준 잘못 발음된 단어와 그 위치. 같은 단어가 여러 번 등장해도 정확한
// 자리만 색칠하기 위해 word + index 두 가지를 같이 받는다. LLM 이 비활성화돼 있으면 빈 배열.
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
        List<WrongWord> wrongWords,
        Instant createdAt
) {

    public static RecordingResponse from(Recording r, List<PhonemeErrorResponse> errors, List<WrongWord> wrongWords) {
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

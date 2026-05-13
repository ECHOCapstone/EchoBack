package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.pronunciation.recording.dto.WrongWord;
import java.util.List;

/**
 * RecordingService.upload 단계에서 LlmClient 가 반환하는 결과.
 *
 * <p>{@code guidanceKr} 는 항상 non-empty 보장 (룰 기반 폴백 포함),
 * {@code wrongWords} 는 약점 단어가 없으면 빈 리스트. 각 항목은 0-based target-word index 를 갖는다
 * (FRONT_API_SPEC §12 `WrongWord`).
 */
public record RecordingGuidance(String guidanceKr, List<WrongWord> wrongWords) {

    public RecordingGuidance {
        if (guidanceKr == null || guidanceKr.isBlank()) {
            throw new IllegalArgumentException("guidanceKr must be non-empty");
        }
        wrongWords = wrongWords == null ? List.of() : List.copyOf(wrongWords);
    }
}

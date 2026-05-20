package com.capstoneecho.echo_back.external.llm;

import java.util.List;

// LlmClient.summarizeRecording 결과. guidanceKr 은 항상 non-empty, wrongWords 는 비어있을 수 있다.
// wrongWords 의 인덱스는 targetText 를 공백 split 한 단어 배열 기준 (0-based).
public record RecordingGuidance(String guidanceKr, List<WrongWord> wrongWords) {

    public RecordingGuidance {
        if (guidanceKr == null || guidanceKr.isBlank()) {
            throw new IllegalArgumentException("guidanceKr must be non-empty");
        }
        wrongWords = wrongWords == null ? List.of() : List.copyOf(wrongWords);
    }
}

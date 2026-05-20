package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// LLM 이 추천한 추가 학습 항목. kind 로 단어 / 구 / 문장을 구분한다.
// reason 은 학습자에게 노출되는 추천 사유 한 줄 (예: "TH 음소 약점 보강").
public record PracticeItem(String text, Kind kind, String reason) {

    public enum Kind { WORD, PHRASE, SENTENCE }

    @JsonCreator
    public PracticeItem(
            @JsonProperty("text") String text,
            @JsonProperty("kind") Kind kind,
            @JsonProperty("reason") String reason) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        this.text = text;
        this.kind = kind;
        this.reason = reason == null ? "" : reason;
    }
}

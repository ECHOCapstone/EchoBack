package com.capstoneecho.echo_back.pronunciation.recording.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FRONT_API_SPEC §12 정렬: LLM 또는 rule-based 폴백이 짚어 준 단어와 그 0-based 단어 인덱스.
 *
 * <p>{@code index} 는 {@code targetText} 를 공백 split 한 단어 배열에서의 위치다.
 */
public record WrongWord(String word, int index) {

    @JsonCreator
    public WrongWord(
            @JsonProperty("word") String word,
            @JsonProperty("index") int index) {
        if (word == null) {
            throw new IllegalArgumentException("word is required");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        this.word = word;
        this.index = index;
    }
}

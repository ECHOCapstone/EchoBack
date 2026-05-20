package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// LLM 이 짚어 준 약점 단어와 그 0-based 단어 인덱스. 인덱스는 targetText 를 공백 split 한 배열 기준이다.
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

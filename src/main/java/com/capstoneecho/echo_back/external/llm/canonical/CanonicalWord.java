package com.capstoneecho.echo_back.external.llm.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 단어 하나와 그 단어의 ARPABET 음소 시퀀스. word 는 표면형 (대소문자 / contraction 보존),
// phonemes 는 강세 표기 없는 베이스 코드 리스트.
public record CanonicalWord(String word, List<String> phonemes) {

    @JsonCreator
    public CanonicalWord(
            @JsonProperty("word") String word,
            @JsonProperty("phonemes") List<String> phonemes) {
        this.word = word == null ? "" : word;
        this.phonemes = phonemes == null ? List.of() : List.copyOf(phonemes);
    }
}

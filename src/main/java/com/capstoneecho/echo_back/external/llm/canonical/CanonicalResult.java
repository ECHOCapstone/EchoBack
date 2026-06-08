package com.capstoneecho.echo_back.external.llm.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 한 문장의 단어별 ARPABET 음소 시퀀스. 채점에서는 단어 경계 보존이 필요하므로 단어 리스트 그대로 전달한다.
public record CanonicalResult(List<CanonicalWord> words) {

    @JsonCreator
    public CanonicalResult(@JsonProperty("words") List<CanonicalWord> words) {
        this.words = words == null ? List.of() : List.copyOf(words);
    }
}

package com.capstoneecho.echo_back.external.llm.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

// 한 문장의 단어별 ARPABET 음소 시퀀스. flatPhonemes() 는 모든 단어를 이어 붙인 문장 단위 시퀀스.
public record CanonicalResult(List<CanonicalWord> words) {

    @JsonCreator
    public CanonicalResult(@JsonProperty("words") List<CanonicalWord> words) {
        this.words = words == null ? List.of() : List.copyOf(words);
    }

    public List<String> flatPhonemes() {
        List<String> flat = new ArrayList<>();
        for (CanonicalWord w : words) {
            flat.addAll(w.phonemes());
        }
        return flat;
    }
}

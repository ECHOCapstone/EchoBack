package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// 한 음소에 대해 LLM 이 제시한 한국식 발음 단서 (아학편 가이드 적용).
// phoneme: ARPAbet 또는 IPA 표기. koreanCue: 한글 음차. tip: 추가 발음 요령 한 줄.
public record PhonemeTip(String phoneme, String koreanCue, String tip) {

    @JsonCreator
    public PhonemeTip(
            @JsonProperty("phoneme") String phoneme,
            @JsonProperty("koreanCue") String koreanCue,
            @JsonProperty("tip") String tip) {
        this.phoneme = phoneme == null ? "" : phoneme;
        this.koreanCue = koreanCue == null ? "" : koreanCue;
        this.tip = tip == null ? "" : tip;
    }
}

package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// 약점 음소에 대한 4파트 발음 교정 가이드.
// correctPronunciation: "이렇게 발음해요" — 정확한 발음 표기.
// perceivedPronunciation: "이렇게 말하신 것 같아요" — 학습자의 실제 발음 표기.
// explanation: 해당 음소의 발음 원리 설명 1~2 문장.
// correctionTip: "X 대신 Y 로 발음해 보세요" 형식의 교정 제안.
public record PronunciationGuide(
        String correctPronunciation,
        String perceivedPronunciation,
        String explanation,
        String correctionTip
) {

    @JsonCreator
    public PronunciationGuide(
            @JsonProperty("correctPronunciation") String correctPronunciation,
            @JsonProperty("perceivedPronunciation") String perceivedPronunciation,
            @JsonProperty("explanation") String explanation,
            @JsonProperty("correctionTip") String correctionTip) {
        this.correctPronunciation = correctPronunciation == null ? "" : correctPronunciation;
        this.perceivedPronunciation = perceivedPronunciation == null ? "" : perceivedPronunciation;
        this.explanation = explanation == null ? "" : explanation;
        this.correctionTip = correctionTip == null ? "" : correctionTip;
    }

    public static PronunciationGuide empty() {
        return new PronunciationGuide("", "", "", "");
    }
}

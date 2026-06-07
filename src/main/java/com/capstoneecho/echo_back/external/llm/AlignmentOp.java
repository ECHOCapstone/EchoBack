package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// LLM 이 만든 canonical ↔ perceived 정렬 한 칸. 사용자에게 노출되는 alignment 시각화의 단일 출처.
//   errorType: MATCH / SUBSTITUTION / INSERTION / DELETION
//   canonical: 정답 음소 (INSERTION 이면 null/blank)
//   perceived: 인식 음소 (DELETION 이면 null/blank)
//   canonicalIndex: canonical 시퀀스 내 0-based 위치 (INSERTION 이면 null)
public record AlignmentOp(
        ErrorType errorType,
        String canonical,
        String perceived,
        Integer canonicalIndex
) {

    public enum ErrorType { MATCH, SUBSTITUTION, INSERTION, DELETION }

    @JsonCreator
    public AlignmentOp(
            @JsonProperty("errorType") ErrorType errorType,
            @JsonProperty("canonical") String canonical,
            @JsonProperty("perceived") String perceived,
            @JsonProperty("canonicalIndex") Integer canonicalIndex) {
        this.errorType = errorType == null ? ErrorType.MATCH : errorType;
        this.canonical = canonical;
        this.perceived = perceived;
        this.canonicalIndex = canonicalIndex;
    }
}

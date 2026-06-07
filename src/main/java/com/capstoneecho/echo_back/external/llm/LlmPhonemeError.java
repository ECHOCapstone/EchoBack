package com.capstoneecho.echo_back.external.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// LLM 이 alignment 에서 추출한 음소 오류 1건 (errors 리스트 항목).
//   op: SUBSTITUTION / INSERTION / DELETION (MATCH 는 errors 에 포함되지 않는다)
//   canonical: 정답 음소 (INSERTION 이면 null)
//   perceived: 인식 음소 (DELETION 이면 null)
//   canonicalIndex: canonical 시퀀스 내 0-based 인덱스 (INSERTION 이면 null)
public record LlmPhonemeError(
        AlignmentOp.ErrorType op,
        String canonical,
        String perceived,
        Integer canonicalIndex
) {

    @JsonCreator
    public LlmPhonemeError(
            @JsonProperty("op") AlignmentOp.ErrorType op,
            @JsonProperty("canonical") String canonical,
            @JsonProperty("perceived") String perceived,
            @JsonProperty("canonicalIndex") Integer canonicalIndex) {
        this.op = op;
        this.canonical = canonical;
        this.perceived = perceived;
        this.canonicalIndex = canonicalIndex;
    }
}

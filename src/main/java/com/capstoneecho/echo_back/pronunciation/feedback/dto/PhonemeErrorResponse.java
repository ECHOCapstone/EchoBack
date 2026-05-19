package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeError;

public record PhonemeErrorResponse(
        String op,
        String canonical,
        String perceived,
        Integer canonicalIndex
) {

    public static PhonemeErrorResponse from(PhonemeError e) {
        return new PhonemeErrorResponse(e.getOp(), e.getCanonical(), e.getPerceived(), e.getCanonicalIndex());
    }
}

package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import java.util.List;

public record RetryWordResult(
        boolean correct,
        List<String> perceived,
        List<String> canonical,
        double score,
        String guidanceKr) {

    public RetryWordResult {
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        canonical = canonical == null ? List.of() : List.copyOf(canonical);
    }
}

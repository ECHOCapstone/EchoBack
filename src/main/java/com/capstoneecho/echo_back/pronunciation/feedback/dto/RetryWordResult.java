package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import java.util.List;

public record RetryWordResult(
        boolean correct,
        List<String> perceived,
        List<String> canonical,
        double score,
        String guidanceKr
) {}

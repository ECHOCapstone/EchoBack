package com.capstoneecho.echo_back.app.feedback.dto;

import java.util.List;

public record RetryWordResponse(
        boolean correct,
        List<String> perceived,
        List<String> canonical,
        double score,
        String guidanceKr
) {}

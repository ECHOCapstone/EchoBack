package com.capstoneecho.echo_back.external.modelserver.dto;

import java.util.List;
import java.util.Optional;

public record AnalyzeResult(
        List<String> perceived,
        Optional<List<String>> canonical,
        List<Double> peakSoftmax,
        List<Object> alignment,
        List<AnalyzeError> errors,
        Optional<Double> per,
        double durationSec
) {}

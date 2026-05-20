package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzeError(
        String op,
        Integer canonicalIndex,
        @JsonAlias("recognized") String perceived,
        String canonical
) {}

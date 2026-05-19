package com.capstoneecho.echo_back.pronunciation.tts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TtsRequest(
        @NotBlank @Size(max = 500) String text,
        String lang
) {}

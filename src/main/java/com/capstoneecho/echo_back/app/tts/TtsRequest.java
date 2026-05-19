package com.capstoneecho.echo_back.app.tts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TtsRequest(
        @NotBlank @Size(max = 500) String text,
        String lang
) {}

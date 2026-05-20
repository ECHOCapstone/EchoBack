package com.capstoneecho.echo_back.learning.session.dto;

import jakarta.validation.constraints.Size;

public record SessionPatchRequest(
        @Size(max = 100)
        String title,

        @Size(max = 5000)
        String scriptText,

        Boolean favorite
) {
}

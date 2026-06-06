package com.capstoneecho.echo_back.translation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 영어 원문 묶음을 받아 한 번에 번역을 요청한다. 한 요청당 상한을 두어 LLM 호출 비용·응답 지연을 제한한다.
public record TranslationRequest(
        @NotNull
        @NotEmpty
        @Size(max = 50)
        List<String> texts
) {}

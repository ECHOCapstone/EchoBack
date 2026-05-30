package com.capstoneecho.echo_back.admin.dto;

import java.util.List;

// 현재 적용 중인 LLM 설정 + 선택 가능한 후보. 어드민 화면이 드롭다운을 그릴 때 쓴다.
public record LlmConfigResponse(
        String provider,
        String model,
        boolean geminiAvailable,
        List<String> providerOptions,
        List<String> modelOptions
) {}

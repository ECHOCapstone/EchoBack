package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.LlmConfigRequest;
import com.capstoneecho.echo_back.admin.dto.LlmConfigResponse;
import com.capstoneecho.echo_back.admin.service.LlmConfigService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 피드백 LLM provider / model 선택. SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/llm")
public class LlmAdminController {

    private final LlmConfigService llmConfigService;

    public LlmAdminController(LlmConfigService llmConfigService) {
        this.llmConfigService = llmConfigService;
    }

    @GetMapping
    public ApiResponse<LlmConfigResponse> get() {
        return ApiResponse.success(llmConfigService.current());
    }

    @PutMapping
    public ApiResponse<LlmConfigResponse> update(@Valid @RequestBody LlmConfigRequest request) {
        return ApiResponse.success(llmConfigService.update(request.provider(), request.model()));
    }
}

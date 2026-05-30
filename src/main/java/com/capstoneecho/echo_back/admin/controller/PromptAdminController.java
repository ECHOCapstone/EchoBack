package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.PromptResponse;
import com.capstoneecho.echo_back.admin.dto.PromptUpdateRequest;
import com.capstoneecho.echo_back.admin.service.PromptAdminService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// LLM 프롬프트 관리. classpath 기본 본문을 런타임에 재정의/초기화한다.
// SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/prompts")
public class PromptAdminController {

    private final PromptAdminService promptAdminService;

    public PromptAdminController(PromptAdminService promptAdminService) {
        this.promptAdminService = promptAdminService;
    }

    @GetMapping
    public ApiResponse<List<PromptResponse>> list() {
        return ApiResponse.success(promptAdminService.list());
    }

    @GetMapping("/{key}")
    public ApiResponse<PromptResponse> get(@PathVariable String key) {
        return ApiResponse.success(promptAdminService.get(key));
    }

    @PutMapping("/{key}")
    public ApiResponse<PromptResponse> override(
            @PathVariable String key, @Valid @RequestBody PromptUpdateRequest request) {
        return ApiResponse.success(promptAdminService.override(key, request.content()));
    }

    // 재정의를 지우고 classpath 기본값으로 되돌린다.
    @DeleteMapping("/{key}")
    public ApiResponse<PromptResponse> reset(@PathVariable String key) {
        return ApiResponse.success(promptAdminService.reset(key));
    }
}

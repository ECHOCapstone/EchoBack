package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.ScriptCreateRequest;
import com.capstoneecho.echo_back.admin.dto.ScriptUpdateRequest;
import com.capstoneecho.echo_back.admin.service.AdminScriptService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.learning.script.service.ScriptService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 트랙 챕터 스크립트 관리. 상세 조회는 기존 ScriptService 를 재사용한다.
// SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/scripts")
public class AdminScriptController {

    private final ScriptService scriptService;
    private final AdminScriptService adminScriptService;

    public AdminScriptController(ScriptService scriptService, AdminScriptService adminScriptService) {
        this.scriptService = scriptService;
        this.adminScriptService = adminScriptService;
    }

    @GetMapping(params = "trackId")
    public ApiResponse<List<ScriptSummaryResponse>> listByTrack(@RequestParam Long trackId) {
        return ApiResponse.success(adminScriptService.listByTrack(trackId));
    }

    @GetMapping("/{scriptId}")
    public ApiResponse<ScriptDetailResponse> get(@PathVariable Long scriptId) {
        return ApiResponse.success(scriptService.getScript(scriptId));
    }

    @PostMapping
    public ApiResponse<ScriptDetailResponse> create(@Valid @RequestBody ScriptCreateRequest request) {
        return ApiResponse.success(adminScriptService.create(request));
    }

    @PutMapping("/{scriptId}")
    public ApiResponse<ScriptDetailResponse> update(
            @PathVariable Long scriptId, @Valid @RequestBody ScriptUpdateRequest request) {
        return ApiResponse.success(adminScriptService.update(scriptId, request));
    }

    @DeleteMapping("/{scriptId}")
    public ApiResponse<Void> delete(@PathVariable Long scriptId) {
        adminScriptService.delete(scriptId);
        return ApiResponse.success(null);
    }
}

package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.SystemStatusResponse;
import com.capstoneecho.echo_back.admin.service.SystemStatusService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 시스템 진단 정보를 한 호출로 모은다. 모델 서버 / LLM / DB(Flyway) / 영구 저장본 상태.
// SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/system")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    public ApiResponse<SystemStatusResponse> status() {
        return ApiResponse.success(systemStatusService.status());
    }
}

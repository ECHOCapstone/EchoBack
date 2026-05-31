package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.ModelServerConfigRequest;
import com.capstoneecho.echo_back.admin.dto.ModelServerConfigResponse;
import com.capstoneecho.echo_back.admin.service.ModelServerConfigService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 음소인식 모델 선택. SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/model-server")
public class ModelServerAdminController {

    private final ModelServerConfigService modelServerConfigService;

    public ModelServerAdminController(ModelServerConfigService modelServerConfigService) {
        this.modelServerConfigService = modelServerConfigService;
    }

    @GetMapping
    public ApiResponse<ModelServerConfigResponse> get() {
        return ApiResponse.success(modelServerConfigService.current());
    }

    @PutMapping
    public ApiResponse<ModelServerConfigResponse> update(
            @Valid @RequestBody ModelServerConfigRequest request) {
        return ApiResponse.success(modelServerConfigService.update(request.model()));
    }
}

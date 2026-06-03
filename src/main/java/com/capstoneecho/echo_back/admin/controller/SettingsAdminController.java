package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.SettingResponse;
import com.capstoneecho.echo_back.admin.dto.SettingUpdateRequest;
import com.capstoneecho.echo_back.admin.service.SettingsAdminService;
import com.capstoneecho.echo_back.admin.service.SettingsPersistService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 게임화 수치 / 메시지 설정 관리. yaml 기본값을 런타임에 재정의/초기화한다.
// SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/settings")
public class SettingsAdminController {

    private final SettingsAdminService settingsAdminService;
    private final SettingsPersistService settingsPersistService;

    public SettingsAdminController(
            SettingsAdminService settingsAdminService,
            SettingsPersistService settingsPersistService
    ) {
        this.settingsAdminService = settingsAdminService;
        this.settingsPersistService = settingsPersistService;
    }

    @GetMapping
    public ApiResponse<List<SettingResponse>> list() {
        return ApiResponse.success(settingsAdminService.list());
    }

    @PutMapping("/{key}")
    public ApiResponse<SettingResponse> update(
            @PathVariable String key, @Valid @RequestBody SettingUpdateRequest request) {
        return ApiResponse.success(settingsAdminService.update(key, request.value()));
    }

    // 재정의를 지우고 yaml 기본값으로 되돌린다.
    @DeleteMapping("/{key}")
    public ApiResponse<SettingResponse> reset(@PathVariable String key) {
        return ApiResponse.success(settingsAdminService.reset(key));
    }

    // 현재 DB 오버라이드를 settings-overrides.yaml 로 영구 저장한다.
    @PostMapping("/persist")
    public ApiResponse<SeedFileStatus> persist() {
        settingsPersistService.persistCurrentStateToFile();
        return ApiResponse.success(settingsPersistService.fileStatus());
    }

    // 모든 오버라이드와 영구 저장본을 지워 yaml 공장 기본값으로 되돌린다.
    @PostMapping("/reset")
    public ApiResponse<SeedFileStatus> resetAll() {
        settingsPersistService.resetToDefaults();
        return ApiResponse.success(settingsPersistService.fileStatus());
    }

    @GetMapping("/seed-info")
    public ApiResponse<SeedFileStatus> seedInfo() {
        return ApiResponse.success(settingsPersistService.fileStatus());
    }
}

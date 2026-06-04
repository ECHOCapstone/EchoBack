package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.BadgeCatalogResponse;
import com.capstoneecho.echo_back.admin.dto.BadgeRequest;
import com.capstoneecho.echo_back.admin.dto.BadgeResponse;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import com.capstoneecho.echo_back.statistics.badges.BadgeCondition;
import com.capstoneecho.echo_back.statistics.badges.BadgeDef;
import com.capstoneecho.echo_back.statistics.badges.BadgeRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 어드민의 시스템 배지(누적/스트릭) CRUD + 영구 저장 / 시드 재적용.
// /api/admin/** 는 SecurityConfig 가 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/badges")
public class BadgeAdminController {

    private final BadgeRegistry badgeRegistry;

    public BadgeAdminController(BadgeRegistry badgeRegistry) {
        this.badgeRegistry = badgeRegistry;
    }

    @GetMapping
    public ApiResponse<BadgeCatalogResponse> list() {
        return ApiResponse.success(new BadgeCatalogResponse(
                badgeRegistry.list().stream().map(BadgeResponse::from).toList(),
                BadgeCondition.allNames()));
    }

    @PostMapping
    public ApiResponse<BadgeResponse> create(@Valid @RequestBody BadgeRequest request) {
        BadgeDef created = badgeRegistry.add(
                new BadgeDef(request.id(), request.name(), request.condition(), request.threshold()));
        return ApiResponse.success(BadgeResponse.from(created));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BadgeResponse> update(@PathVariable String id, @Valid @RequestBody BadgeRequest request) {
        BadgeDef updated = badgeRegistry.update(id,
                new BadgeDef(id, request.name(), request.condition(), request.threshold()));
        return ApiResponse.success(BadgeResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        badgeRegistry.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/persist")
    public ApiResponse<SeedFileStatus> persist() {
        badgeRegistry.persistCurrentStateToFile();
        return ApiResponse.success(badgeRegistry.fileStatus());
    }

    @PostMapping("/reset")
    public ApiResponse<SeedFileStatus> reset() {
        badgeRegistry.resetToDefaults();
        return ApiResponse.success(badgeRegistry.fileStatus());
    }
}

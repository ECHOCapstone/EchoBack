package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.TrackCreateRequest;
import com.capstoneecho.echo_back.admin.dto.TrackUpdateRequest;
import com.capstoneecho.echo_back.admin.service.AdminTrackService;
import com.capstoneecho.echo_back.admin.service.TrackPersistService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import com.capstoneecho.echo_back.learning.track.dto.TrackSummaryResponse;
import com.capstoneecho.echo_back.learning.track.service.TrackService;
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

// 트랙 관리 (목록/생성/수정/삭제). SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/tracks")
public class AdminTrackController {

    private final TrackService trackService;
    private final AdminTrackService adminTrackService;
    private final TrackPersistService trackPersistService;

    public AdminTrackController(
            TrackService trackService,
            AdminTrackService adminTrackService,
            TrackPersistService trackPersistService
    ) {
        this.trackService = trackService;
        this.adminTrackService = adminTrackService;
        this.trackPersistService = trackPersistService;
    }

    @GetMapping
    public ApiResponse<List<TrackSummaryResponse>> list() {
        return ApiResponse.success(trackService.listTracks());
    }

    @PostMapping
    public ApiResponse<TrackSummaryResponse> create(@Valid @RequestBody TrackCreateRequest request) {
        return ApiResponse.success(adminTrackService.create(request));
    }

    @PutMapping("/{trackId}")
    public ApiResponse<TrackSummaryResponse> update(
            @PathVariable Long trackId, @Valid @RequestBody TrackUpdateRequest request) {
        return ApiResponse.success(adminTrackService.update(trackId, request));
    }

    @DeleteMapping("/{trackId}")
    public ApiResponse<Void> delete(@PathVariable Long trackId) {
        adminTrackService.delete(trackId);
        return ApiResponse.success(null);
    }

    @PostMapping("/persist")
    public ApiResponse<SeedFileStatus> persist() {
        trackPersistService.persistCurrentStateToFile();
        return ApiResponse.success(trackPersistService.fileStatus());
    }

    @PostMapping("/reset")
    public ApiResponse<SeedFileStatus> reset() {
        trackPersistService.resetToDefaults();
        return ApiResponse.success(trackPersistService.fileStatus());
    }

    @GetMapping("/seed-info")
    public ApiResponse<SeedFileStatus> seedInfo() {
        return ApiResponse.success(trackPersistService.fileStatus());
    }
}

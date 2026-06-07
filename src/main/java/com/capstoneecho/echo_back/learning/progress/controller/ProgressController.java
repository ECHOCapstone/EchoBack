package com.capstoneecho.echo_back.learning.progress.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.learning.progress.dto.AdvanceProgressRequest;
import com.capstoneecho.echo_back.learning.progress.dto.ProgressResponse;
import com.capstoneecho.echo_back.learning.progress.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 챕터 / 세션 학습 진행 상태 진입점. 두 도메인 모두 같은 4 엔드포인트 패턴을 따른다.
//   GET    /api/scripts/{id}/progress         — 마지막 완료 step 인덱스 조회
//   POST   /api/scripts/{id}/progress/advance — 새 step 완료 보고
//   DELETE /api/scripts/{id}/progress         — "처음부터" 또는 챕터 완료 후 정리
//   세션은 /api/sessions/{id}/progress/*.
@RestController
@RequestMapping("/api")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/scripts/{scriptId}/progress")
    public ApiResponse<ProgressResponse> getChapterProgress(
            @PathVariable Long scriptId,
            @CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(progressService.getChapterProgress(principal.userId(), scriptId));
    }

    @PostMapping("/scripts/{scriptId}/progress/advance")
    public ApiResponse<ProgressResponse> advanceChapter(
            @PathVariable Long scriptId,
            @Valid @RequestBody AdvanceProgressRequest request,
            @CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(progressService.advanceChapter(principal.userId(), scriptId, request.index()));
    }

    @DeleteMapping("/scripts/{scriptId}/progress")
    public ApiResponse<Void> resetChapter(
            @PathVariable Long scriptId,
            @CurrentUser JwtPrincipal principal) {
        progressService.resetChapter(principal.userId(), scriptId);
        return ApiResponse.success(null);
    }

    @GetMapping("/sessions/{sessionId}/progress")
    public ApiResponse<ProgressResponse> getSessionProgress(
            @PathVariable Long sessionId,
            @CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(progressService.getSessionProgress(principal.userId(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/progress/advance")
    public ApiResponse<ProgressResponse> advanceSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody AdvanceProgressRequest request,
            @CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(progressService.advanceSession(principal.userId(), sessionId, request.index()));
    }

    @DeleteMapping("/sessions/{sessionId}/progress")
    public ApiResponse<Void> resetSession(
            @PathVariable Long sessionId,
            @CurrentUser JwtPrincipal principal) {
        progressService.resetSession(principal.userId(), sessionId);
        return ApiResponse.success(null);
    }
}

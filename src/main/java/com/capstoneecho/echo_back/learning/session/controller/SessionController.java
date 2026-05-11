package com.capstoneecho.echo_back.learning.session.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.learning.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionDetailResponse;
import com.capstoneecho.echo_back.learning.session.dto.SessionPatchRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionSummaryResponse;
import com.capstoneecho.echo_back.learning.session.service.SessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ApiResponse<List<SessionSummaryResponse>> list(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(sessionService.list(principal.userId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SessionDetailResponse>> create(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody SessionCreateRequest request) {
        SessionDetailResponse created = sessionService.create(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> get(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId) {
        return ApiResponse.success(sessionService.get(principal.userId(), sessionId));
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> patch(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionPatchRequest request) {
        return ApiResponse.success(sessionService.patch(principal.userId(), sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId) {
        sessionService.delete(principal.userId(), sessionId);
        return ApiResponse.success(null);
    }
}

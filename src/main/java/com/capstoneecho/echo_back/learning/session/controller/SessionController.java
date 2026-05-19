package com.capstoneecho.echo_back.learning.session.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.learning.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionDetailResponse;
import com.capstoneecho.echo_back.learning.session.dto.SessionPatchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.capstoneecho.echo_back.learning.session.service.SessionService;
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ApiResponse<List<SessionDetailResponse>> list(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(sessionService.listMine(principal.userId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionDetailResponse> create(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody SessionCreateRequest request
    ) {
        return ApiResponse.success(sessionService.create(principal.userId(), request));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> get(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(sessionService.get(principal.userId(), sessionId));
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> update(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionPatchRequest request
    ) {
        return ApiResponse.success(sessionService.update(principal.userId(), sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId
    ) {
        sessionService.delete(principal.userId(), sessionId);
        return ApiResponse.success(null);
    }
}

package com.capstoneecho.echo_back.app.session;

import com.capstoneecho.echo_back.app.common.ApiResponse;
import com.capstoneecho.echo_back.app.jwt.CurrentUser;
import com.capstoneecho.echo_back.app.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.app.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.app.session.dto.SessionResponse;
import com.capstoneecho.echo_back.app.session.dto.SessionUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ApiResponse<List<SessionResponse>> list(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.ok(sessionService.listMine(principal.userId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> create(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody SessionCreateRequest request
    ) {
        return ApiResponse.ok(sessionService.create(principal.userId(), request));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionResponse> get(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.ok(sessionService.get(principal.userId(), sessionId));
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<SessionResponse> update(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionUpdateRequest request
    ) {
        return ApiResponse.ok(sessionService.update(principal.userId(), sessionId, request));
    }
}

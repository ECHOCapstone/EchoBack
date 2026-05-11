package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.EmailCheckRequest;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.UsernameCheckRequest;
import com.capstoneecho.echo_back.member.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        AuthTokenResponse token = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(token));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/check-username")
    public ApiResponse<AvailabilityResponse> checkUsername(
            @Valid @RequestBody UsernameCheckRequest request) {
        return ApiResponse.success(authService.checkUsername(request.username()));
    }

    @PostMapping("/check-email")
    public ApiResponse<AvailabilityResponse> checkEmail(
            @Valid @RequestBody EmailCheckRequest request) {
        return ApiResponse.success(authService.checkEmail(request.email()));
    }

    @GetMapping("/oauth2/google/demo")
    public ApiResponse<AuthTokenResponse> oauth2GoogleDemo() {
        return ApiResponse.success(authService.loginWithGoogleDemo());
    }
}

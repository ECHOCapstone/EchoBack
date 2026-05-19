package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.member.dto.CheckRequest;
import com.capstoneecho.echo_back.member.dto.CheckResponse;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.TokenResponse;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstoneecho.echo_back.member.service.AuthService;
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/check-username")
    public ApiResponse<CheckResponse> checkUsername(@Valid @RequestBody CheckRequest request) {
        return ApiResponse.ok(new CheckResponse(authService.isUsernameAvailable(request.value())));
    }

    @PostMapping("/check-email")
    public ApiResponse<CheckResponse> checkEmail(@Valid @RequestBody CheckRequest request) {
        return ApiResponse.ok(new CheckResponse(authService.isEmailAvailable(request.value())));
    }

    @GetMapping("/oauth2/google/demo")
    public ApiResponse<TokenResponse> demoGoogleLogin() {
        return ApiResponse.ok(authService.demoGoogleLogin());
    }
}

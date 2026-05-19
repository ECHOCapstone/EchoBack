package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.EmailCheckRequest;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.UsernameCheckRequest;
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
    public ApiResponse<AuthTokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/check-username")
    public ApiResponse<AvailabilityResponse> checkUsername(@Valid @RequestBody UsernameCheckRequest request) {
        return ApiResponse.success(new AvailabilityResponse(authService.isUsernameAvailable(request.value())));
    }

    @PostMapping("/check-email")
    public ApiResponse<AvailabilityResponse> checkEmail(@Valid @RequestBody EmailCheckRequest request) {
        return ApiResponse.success(new AvailabilityResponse(authService.isEmailAvailable(request.value())));
    }

    @GetMapping("/oauth2/google/demo")
    public ApiResponse<AuthTokenResponse> demoGoogleLogin() {
        return ApiResponse.success(authService.demoGoogleLogin());
    }
}

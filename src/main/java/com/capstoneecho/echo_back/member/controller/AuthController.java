package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.EmailCheckRequest;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.OAuth2SignupCompleteRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.UsernameCheckRequest;
import com.capstoneecho.echo_back.member.service.AuthService;
import com.capstoneecho.echo_back.member.service.OAuth2SignupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuth2SignupService oauth2SignupService;

    public AuthController(AuthService authService, OAuth2SignupService oauth2SignupService) {
        this.authService = authService;
        this.oauth2SignupService = oauth2SignupService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        AuthTokenResponse token = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(token));
    }

    // OAuth2 신규 사용자가 가입 폼을 완료할 때 호출한다. pendingToken 으로 신원을 복원하고
    // 사용자가 직접 입력한 username/nickname/agreedTerms 를 합쳐 User + SocialAccount 를 생성한다.
    @PostMapping("/oauth2/signup-complete")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> oauth2SignupComplete(
            @Valid @RequestBody OAuth2SignupCompleteRequest request) {
        AuthTokenResponse token = oauth2SignupService.complete(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(token));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/check-username")
    public ApiResponse<AvailabilityResponse> checkUsername(
            @Valid @RequestBody UsernameCheckRequest request) {
        return ApiResponse.success(authService.checkUsername(request.value()));
    }

    @PostMapping("/check-email")
    public ApiResponse<AvailabilityResponse> checkEmail(
            @Valid @RequestBody EmailCheckRequest request) {
        return ApiResponse.success(authService.checkEmail(request.value()));
    }
}

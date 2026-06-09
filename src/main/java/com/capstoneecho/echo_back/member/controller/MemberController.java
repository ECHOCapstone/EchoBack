package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.member.dto.ChangePasswordRequest;
import com.capstoneecho.echo_back.member.dto.NicknameUpdateRequest;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.dto.WithdrawRequest;
import com.capstoneecho.echo_back.member.service.AuthService;
import com.capstoneecho.echo_back.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;

    public MemberController(MemberService memberService, AuthService authService) {
        this.memberService = memberService;
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(memberService.findMe(principal.userId()));
    }

    @PatchMapping("/me/nickname")
    public ApiResponse<UserResponse> updateNickname(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody NicknameUpdateRequest request) {
        return ApiResponse.success(memberService.updateNickname(principal.userId(), request));
    }

    // 온보딩 튜토리얼을 마친 사용자가 호출한다. 멱등 — 이미 완료한 사용자가 다시 호출해도 안전하다.
    @PatchMapping("/me/onboarding-completed")
    public ApiResponse<UserResponse> completeOnboarding(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(memberService.completeOnboarding(principal.userId()));
    }

    // 로그인된 사용자가 비밀번호를 교체한다. 현재 비밀번호 확인 + 정책 검증을 통과해야 적용된다.
    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
        return ApiResponse.success(null);
    }

    // 회원 탈퇴. 비밀번호 기반 계정은 본문에 currentPassword 를 함께 보내야 한다 (사고성 탈퇴 방지).
    // soft delete 라 학습 기록은 30일 grace period 동안 보존된다.
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
            @CurrentUser JwtPrincipal principal,
            @RequestBody(required = false) WithdrawRequest request) {
        authService.withdraw(principal.userId(), request);
        return ApiResponse.success(null);
    }
}

package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.dto.NicknameUpdateRequest;
import com.capstoneecho.echo_back.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
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
}

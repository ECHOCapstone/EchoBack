package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.member.dto.UpdateNicknameRequest;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstoneecho.echo_back.member.service.MemberService;
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser JwtPrincipal principal) {
        var user = memberService.getById(principal.userId());
        return ApiResponse.success(UserResponse.from(user));
    }

    @PatchMapping("/me/nickname")
    public ApiResponse<UserResponse> changeNickname(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody UpdateNicknameRequest request
    ) {
        var user = memberService.updateNickname(principal.userId(), request.nickname());
        return ApiResponse.success(UserResponse.from(user));
    }
}

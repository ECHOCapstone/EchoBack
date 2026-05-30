package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 진입점. SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
// 향후 콘텐츠 / 설정 / 프롬프트 등 관리 엔드포인트가 이 경로 아래에 추가된다.
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final MemberService memberService;

    public AdminController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 관리자 권한 확인용. 프론트가 어드민 화면 진입 전에 이 호출로 ROLE_ADMIN 여부를 검증한다.
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(memberService.findMe(principal.userId()));
    }
}

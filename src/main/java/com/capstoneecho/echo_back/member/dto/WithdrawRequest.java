package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.Size;

// 회원 탈퇴 요청. 비밀번호 기반 계정은 현재 비밀번호를 한 번 더 확인해 사고성 탈퇴를 막는다.
// OAuth 전용 계정은 비밀번호가 없으므로 password 가 null/blank 로 들어와도 허용한다.
public record WithdrawRequest(
        @Size(max = 100)
        String currentPassword
) {
}

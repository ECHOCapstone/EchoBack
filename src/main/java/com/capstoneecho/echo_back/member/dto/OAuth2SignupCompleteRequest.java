package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// OAuth2 신규 사용자가 가입 폼에서 제출하는 입력.
//   pendingToken: 백엔드 FailureHandler 가 발급한 5분짜리 JWT (email / provider / providerUid 포함)
//   username:     사용자가 직접 정한 아이디 — 통합 회원 풀에서 unique
//   nickname:     사용자가 정한 표시명 (Google/Kakao 가 준 이름은 nicknameHint 로만 사용)
//   agreedTerms:  약관 동의 — 표준 가입과 일관성 유지를 위해 명시적으로 받는다
public record OAuth2SignupCompleteRequest(
        @NotBlank
        String pendingToken,

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Size(max = 30)
        String nickname,

        @AssertTrue(message = "약관 동의가 필요합니다.")
        boolean agreedTerms
) {
}

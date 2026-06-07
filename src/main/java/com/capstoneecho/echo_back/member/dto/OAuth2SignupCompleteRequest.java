package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// OAuth2 신규 사용자가 가입 폼에서 제출하는 입력.
//   pendingToken     : 백엔드 FailureHandler 가 발급한 5분짜리 JWT (email / provider / providerUid 포함)
//   nickname         : 사용자가 정한 표시명 (Google/Kakao 가 준 이름은 nicknameHint 로만 사용)
//   동의 필드 4종    : 표준 가입과 동일한 동의 모델 — 약관 / 개인정보 / 14세 필수, 마케팅 선택.
// 아이디(username)는 별도로 받지 않는다 — provider + providerUid 가 이미 식별자 역할을 하므로
// 내부 username 은 백엔드가 결정적으로 자동 생성한다 (표준 소셜 로그인 온보딩 모델).
public record OAuth2SignupCompleteRequest(
        @NotBlank
        String pendingToken,

        @NotBlank
        @Size(max = 30)
        String nickname,

        @AssertTrue(message = "이용약관 동의가 필요합니다.")
        boolean agreedTerms,

        @AssertTrue(message = "개인정보처리방침 동의가 필요합니다.")
        boolean agreedPrivacy,

        @AssertTrue(message = "만 14세 이상 확인이 필요합니다.")
        boolean agreedAgeOver14,

        @NotNull
        Boolean agreedMarketing
) {
}

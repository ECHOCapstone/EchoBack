package com.capstoneecho.echo_back.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 표준 회원가입 입력. 비밀번호 정책 검증은 PasswordPolicyEvaluator 가 별도로 수행해
// @Size 만으로는 잡지 못하는 문자 카테고리 규칙까지 검사한다.
//
// 동의 필드는 모두 Boolean wrapper + @NotNull. Jackson 3 는 누락된 primitive boolean 을
// false 로 자동 변환하지 않고 거부하므로, 누락 요청은 명시적으로 VALIDATION_FAILED 로 끊는다.
public record SignupRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Email
        @Size(max = 100)
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @NotBlank
        @Size(max = 30)
        String nickname,

        // 이용약관 동의 (필수). false 면 가입 거절.
        @NotNull
        @AssertTrue(message = "이용약관 동의가 필요합니다.")
        Boolean agreedTerms,

        // 개인정보처리방침 동의 (필수). false 면 가입 거절.
        @NotNull
        @AssertTrue(message = "개인정보처리방침 동의가 필요합니다.")
        Boolean agreedPrivacy,

        // 만 14세 이상 동의 (필수, 한국 PIPA). false 면 가입 거절.
        @NotNull
        @AssertTrue(message = "만 14세 이상 확인이 필요합니다.")
        Boolean agreedAgeOver14,

        // 마케팅 정보 수신 동의 (선택). 필드 자체는 항상 들어와야 false/true 명시 처리가 가능하다.
        @NotNull
        Boolean agreedMarketing
) {
}

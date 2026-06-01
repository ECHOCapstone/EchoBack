package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.Provider;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

// OAuth2 인증은 성공했지만 우리 도메인에 사용자가 없는 신규 가입 케이스를 표현하는 예외.
// FailureHandler 가 이 예외를 받으면 회원가입 페이지로 사용자를 안내한다.
//
// errorCode 는 "pending_signup_required" 로 고정하고, 추가 정보 (email, provider, providerUid,
// nicknameHint) 는 예외 필드로 노출해 FailureHandler 가 pending JWT 에 그대로 담아 프론트로 전달한다.
public class PendingSignupException extends OAuth2AuthenticationException {

    public static final String ERROR_CODE = "pending_signup_required";

    private final transient Provider provider;
    private final String providerUid;
    private final String email;
    private final String nicknameHint;

    public PendingSignupException(Provider provider, String providerUid, String email, String nicknameHint) {
        super(new OAuth2Error(ERROR_CODE));
        this.provider = provider;
        this.providerUid = providerUid;
        this.email = email;
        this.nicknameHint = nicknameHint;
    }

    public Provider provider() { return provider; }
    public String providerUid() { return providerUid; }
    public String email() { return email; }
    public String nicknameHint() { return nicknameHint; }
}

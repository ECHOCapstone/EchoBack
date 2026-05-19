package com.capstoneecho.echo_back.app.auth;

import com.capstoneecho.echo_back.app.auth.dto.LoginRequest;
import com.capstoneecho.echo_back.app.auth.dto.SignupRequest;
import com.capstoneecho.echo_back.app.auth.dto.TokenResponse;

// 인증 도메인의 외부 노출 인터페이스. 모든 구현체는 동일한 토큰 응답 계약을 따른다.
public interface AuthService {

    TokenResponse signup(SignupRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse demoGoogleLogin();

    boolean isUsernameAvailable(String username);

    boolean isEmailAvailable(String email);
}

package com.capstoneecho.echo_back.app.auth;

import com.capstoneecho.echo_back.app.auth.dto.LoginRequest;
import com.capstoneecho.echo_back.app.auth.dto.SignupRequest;
import com.capstoneecho.echo_back.app.auth.dto.TokenResponse;
import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.jwt.JwtProvider;
import com.capstoneecho.echo_back.app.member.User;
import com.capstoneecho.echo_back.app.member.UserRepository;
import com.capstoneecho.echo_back.app.member.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class AuthServiceImpl implements AuthService {

    // 시연용 OAuth 가짜 사용자. 실제 구글 OAuth 흐름은 백엔드 개발자가 추후 구현한다.
    private static final String DEMO_GOOGLE_USERNAME = "google_demo";
    private static final String DEMO_GOOGLE_EMAIL = "demo@google.example";
    private static final String DEMO_GOOGLE_NICKNAME = "구글 데모";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        var saved = userRepository.save(User.create(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        ));
        return issue(saved);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        var user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return issue(user);
    }

    @Override
    public TokenResponse demoGoogleLogin() {
        var user = userRepository.findByUsername(DEMO_GOOGLE_USERNAME)
                .orElseGet(() -> userRepository.save(User.create(
                        DEMO_GOOGLE_USERNAME,
                        DEMO_GOOGLE_EMAIL,
                        passwordEncoder.encode("demo-oauth"),
                        DEMO_GOOGLE_NICKNAME
                )));
        return issue(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    private TokenResponse issue(User user) {
        var token = jwtProvider.issue(user.getId(), user.getUsername());
        return TokenResponse.of(token, jwtProvider.expiresInSec(), UserResponse.from(user));
    }
}

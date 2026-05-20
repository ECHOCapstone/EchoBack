package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 인증 도메인의 단일 진입점. JWT 발급, 회원 가입 / 로그인 / OAuth2 시연, 중복 확인을 책임진다.
// 시연용 OAuth2 사용자의 식별자는 AppProperties.auth.demoGoogle 로 외부화되어 하드코딩을 피한다.
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final long jwtExpirationMs;
    private final AppProperties.Auth.DemoGoogle demoGoogle;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.jwtExpirationMs = appProperties.jwt().expirationMs();
        this.demoGoogle = appProperties.auth() == null ? null : appProperties.auth().demoGoogle();
    }

    public AuthTokenResponse signup(SignupRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        String hash = passwordEncoder.encode(request.password());
        User saved = userRepository.save(
                User.signup(request.username(), request.email(), hash, request.nickname()));

        return issueToken(saved);
    }

    public AuthTokenResponse login(LoginRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        String storedHash = user.getPasswordHash();
        if (storedHash == null || storedHash.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.password(), storedHash)) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        return issueToken(user);
    }

    // 시연용 OAuth2 사용자 자동 가입 / 로그인. 식별자는 AppProperties 에서 받아 환경별로 조정 가능하다.
    public AuthTokenResponse loginWithGoogleDemo() {
        if (demoGoogle == null
                || demoGoogle.email() == null || demoGoogle.email().isBlank()
                || demoGoogle.nickname() == null || demoGoogle.nickname().isBlank()) {
            throw new IllegalStateException("app.auth.demo-google.email / nickname must be configured");
        }
        String demoEmail = demoGoogle.email();
        String demoNickname = demoGoogle.nickname();
        User user = userRepository.findByEmail(demoEmail)
                .map(existing -> {
                    existing.mergeOAuth2Login();
                    return existing;
                })
                .orElseGet(() -> userRepository.save(
                        User.fromOAuth2(demoEmail, demoNickname)));
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return new AvailabilityResponse(!userRepository.existsByUsername(username));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return new AvailabilityResponse(!userRepository.existsByEmail(email));
    }

    private AuthTokenResponse issueToken(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        String token = jwtProvider.issue(user.getId(), claims);
        long expiresInSec = Math.max(0L, jwtExpirationMs / 1000L);
        return AuthTokenResponse.of(token, expiresInSec, UserResponse.from(user));
    }
}

package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstoneecho.echo_back.member.repository.UserRepository;
@Service
@Transactional
class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AppProperties.DemoGoogle demoGoogle;

    AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            AppProperties properties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.demoGoogle = properties.auth().demoGoogle();
    }

    @Override
    public AuthTokenResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        // exists 체크와 save 사이에 동시 가입이 들어오면 UNIQUE 제약 위반이 발생하는데,
        // 그것도 도메인이 의미하는 "중복" 으로 일관 응답하기 위해 명시적으로 변환한다.
        try {
            var saved = userRepository.save(User.signup(
                    request.username(),
                    request.email(),
                    passwordEncoder.encode(request.password()),
                    request.nickname()
            ));
            return issue(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED, "이미 사용 중인 아이디 또는 이메일입니다.");
        }
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        var user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return issue(user);
    }

    @Override
    public AuthTokenResponse demoGoogleLogin() {
        var user = userRepository.findByUsername(demoGoogle.username())
                .orElseGet(() -> userRepository.save(User.signup(
                        demoGoogle.username(),
                        demoGoogle.email(),
                        passwordEncoder.encode(demoGoogle.password()),
                        demoGoogle.nickname()
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

    private AuthTokenResponse issue(User user) {
        var token = jwtProvider.issue(user.getId(), user.getUsername());
        return AuthTokenResponse.of(token, jwtProvider.expiresInSec(), UserResponse.from(user));
    }
}

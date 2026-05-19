package com.capstoneecho.echo_back.app.auth;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.auth.dto.LoginRequest;
import com.capstoneecho.echo_back.app.auth.dto.SignupRequest;
import com.capstoneecho.echo_back.app.auth.dto.TokenResponse;
import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.jwt.JwtProvider;
import com.capstoneecho.echo_back.app.member.User;
import com.capstoneecho.echo_back.app.member.UserRepository;
import com.capstoneecho.echo_back.app.member.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        // exists 체크와 save 사이에 동시 가입이 들어오면 UNIQUE 제약 위반이 발생하는데,
        // 그것도 도메인이 의미하는 "중복" 으로 일관 응답하기 위해 명시적으로 변환한다.
        try {
            var saved = userRepository.save(User.create(
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
        var user = userRepository.findByUsername(demoGoogle.username())
                .orElseGet(() -> userRepository.save(User.create(
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

    private TokenResponse issue(User user) {
        var token = jwtProvider.issue(user.getId(), user.getUsername());
        return TokenResponse.of(token, jwtProvider.expiresInSec(), UserResponse.from(user));
    }
}

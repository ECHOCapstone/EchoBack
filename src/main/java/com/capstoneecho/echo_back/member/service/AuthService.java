package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.MemberProfileResponse;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final long jwtExpirationMs;

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
        User user = findByUsernameOrEmail(request.usernameOrEmail())
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

    private Optional<User> findByUsernameOrEmail(String value) {
        Optional<User> byUsername = userRepository.findByUsername(value);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return userRepository.findByEmail(value);
    }

    private AuthTokenResponse issueToken(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        String token = jwtProvider.issue(user.getId(), claims);
        Instant expiresAt = Instant.now().plusMillis(jwtExpirationMs);
        return new AuthTokenResponse(token, expiresAt, MemberProfileResponse.from(user));
    }
}

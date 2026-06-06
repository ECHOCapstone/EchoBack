package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.global.security.password.PasswordPolicyEvaluator;
import com.capstoneecho.echo_back.legal.LegalTermsRegistry;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.ChangePasswordRequest;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.dto.WithdrawRequest;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 인증 도메인의 단일 진입점. JWT 발급, 회원 가입 / 로그인, 중복 확인을 책임진다.
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final PasswordPolicyEvaluator passwordPolicyEvaluator;
    private final LegalTermsRegistry legalTermsRegistry;
    private final long jwtExpirationMs;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            PasswordPolicyEvaluator passwordPolicyEvaluator,
            LegalTermsRegistry legalTermsRegistry,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.passwordPolicyEvaluator = passwordPolicyEvaluator;
        this.legalTermsRegistry = legalTermsRegistry;
        this.jwtExpirationMs = appProperties.jwt().expirationMs();
    }

    public AuthTokenResponse signup(SignupRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // 비밀번호 정책 — @Size 만으로는 잡지 못하는 문자 카테고리 규칙까지 검사한다.
        PasswordPolicyEvaluator.Result policyResult = passwordPolicyEvaluator.evaluate(request.password());
        if (!policyResult.valid()) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, policyResult.reason());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        String hash = passwordEncoder.encode(request.password());
        User created = User.signup(request.username(), request.email(), hash, request.nickname());
        // 동의 시점을 같은 트랜잭션에서 한 묶음으로 기록한다 (필수 3종 + 선택 1종).
        created.applyAgreements(buildAgreementRecord(
                request.agreedTerms(),
                request.agreedPrivacy(),
                request.agreedAgeOver14(),
                Boolean.TRUE.equals(request.agreedMarketing())));
        User saved = userRepository.save(created);

        return issueToken(saved);
    }

    // 표준 가입 / OAuth 가입 양쪽에서 동의 시점을 기록할 때 공통으로 쓰는 헬퍼.
    // 필수 동의 3종은 가입 흐름의 사전 검증(@AssertTrue)으로 보장되며, 이 메서드는 시간만 부여한다.
    public User.AgreementRecord buildAgreementRecord(
            boolean termsAgreed, boolean privacyAgreed, boolean ageOver14Agreed, boolean marketingAgreed) {
        if (!termsAgreed) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }
        if (!privacyAgreed) {
            throw new BusinessException(ErrorCode.PRIVACY_NOT_AGREED);
        }
        if (!ageOver14Agreed) {
            throw new BusinessException(ErrorCode.AGE_RESTRICTION_NOT_AGREED);
        }
        Instant now = Instant.now();
        return new User.AgreementRecord(
                legalTermsRegistry.version(),
                now,
                now,
                now,
                marketingAgreed ? now : null);
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

        // 탈퇴 grace period 안에 같은 비밀번호로 다시 로그인 — 자동 복구한다.
        // 비밀번호 매칭이 본인 확인을 보장하므로 추가 단계 없이 안전하다.
        if (user.isDeleted()) {
            user.restore();
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

    // 로그인된 사용자가 자신의 비밀번호를 교체한다.
    // OAuth 전용 계정 (passwordHash == null) 은 일반 비밀번호 흐름이 없으므로 거절한다 (별도 "비밀번호 설정" 흐름 필요).
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        String storedHash = user.getPasswordHash();
        if (storedHash == null || storedHash.isBlank()) {
            // 소셜 전용 계정은 변경할 비밀번호가 없다 — 별도 "비밀번호 설정" 흐름으로 안내한다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "소셜 로그인 전용 계정은 비밀번호 변경 흐름을 사용할 수 없습니다.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), storedHash)) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "현재 비밀번호가 일치하지 않습니다.");
        }
        PasswordPolicyEvaluator.Result policyResult = passwordPolicyEvaluator.evaluate(request.newPassword());
        if (!policyResult.valid()) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, policyResult.reason());
        }
        if (passwordEncoder.matches(request.newPassword(), storedHash)) {
            // 현재 비밀번호와 동일한 새 비밀번호는 보안 이점이 없어 거절한다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "새 비밀번호가 현재 비밀번호와 동일합니다.");
        }
        user.replacePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    // 회원 탈퇴 — soft delete 로 deleted_at 을 채운다.
    // 비밀번호 기반 계정은 현재 비밀번호 재확인을 요구해 사고성 탈퇴를 차단한다.
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        String storedHash = user.getPasswordHash();
        boolean hasPassword = storedHash != null && !storedHash.isBlank();
        if (hasPassword) {
            String typed = request == null ? null : request.currentPassword();
            if (typed == null || typed.isBlank()
                    || !passwordEncoder.matches(typed, storedHash)) {
                throw new BusinessException(ErrorCode.LOGIN_FAILED, "현재 비밀번호가 일치하지 않습니다.");
            }
        }
        user.markDeleted(Instant.now());
    }

    private AuthTokenResponse issueToken(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        String token = jwtProvider.issue(user.getId(), claims);
        long expiresInSec = Math.max(0L, jwtExpirationMs / 1000L);
        return AuthTokenResponse.of(token, expiresInSec, UserResponse.from(user));
    }
}

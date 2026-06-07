package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.global.security.oauth2.PendingOAuthTokenService;
import com.capstoneecho.echo_back.global.security.oauth2.PendingOAuthTokenService.PendingOAuthClaims;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.OAuth2SignupCompleteRequest;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// OAuth2 신규 사용자 가입 폼 제출을 처리한다.
// pendingToken 으로 provider / providerUid / email 을 신뢰 가능하게 복원하고,
// 사용자가 직접 정한 nickname + agreedTerms 검증 후 User + SocialAccount 를 동시 생성한다.
// 내부 username 은 User entity 가 (provider, providerUid) 로부터 자동 생성한다 — 사용자에게 노출되지 않는다.
// 응답은 표준 가입과 동일한 AuthTokenResponse — 프론트가 한 번의 흐름으로 메인 화면 진입 가능.
@Service
public class OAuth2SignupService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PendingOAuthTokenService pendingTokenService;
    private final JwtProvider jwtProvider;
    private final AuthService authService;
    private final long jwtExpirationMs;

    public OAuth2SignupService(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository,
            PendingOAuthTokenService pendingTokenService,
            JwtProvider jwtProvider,
            AuthService authService,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.pendingTokenService = pendingTokenService;
        this.jwtProvider = jwtProvider;
        this.authService = authService;
        this.jwtExpirationMs = appProperties.jwt().expirationMs();
    }

    @Transactional
    public AuthTokenResponse complete(OAuth2SignupCompleteRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // pending 토큰 검증 — 변조 / 만료 / 다른 종류의 토큰이면 INVALID_TOKEN.
        PendingOAuthClaims pending = pendingTokenService.verify(request.pendingToken());
        // provider 가 검증한 이메일을 표준 회원 풀과 같은 규칙으로 정규화한다 (case-insensitive + trim).
        String email = normalizeEmail(pending.email());

        // 이메일 / provider+sub 중복 — race condition 안전망. unique 제약이 최종 SSOT.
        // username 은 (provider, providerUid) 로부터 결정적으로 생성되므로 이 두 검사로 username 충돌도 함께 막힌다.
        if (userRepository.existsByEmail(email)) {
            // 같은 이메일이 이미 가입돼 있음 — Case B 가 되었어야 하는데 race 로 끼어든 상황.
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        // 동일 provider 의 sub 가 이미 등록돼 있으면 Case A 였어야 함 — 안전망.
        // 이메일 중복과 의미가 다르므로 별도 ErrorCode 로 알려 FE 가 정확한 안내를 띄울 수 있게 한다.
        if (socialAccountRepository
                .findByProviderAndProviderUid(pending.provider(), pending.providerUid())
                .isPresent()) {
            throw new BusinessException(ErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED);
        }

        User newUser = User.fromOAuth2Signup(
                pending.provider(), pending.providerUid(), email, request.nickname());
        // 표준 가입과 동일한 동의 모델 — 시간을 한 번에 기록한다.
        newUser.applyAgreements(authService.buildAgreementRecord(
                request.agreedTerms(),
                request.agreedPrivacy(),
                request.agreedAgeOver14(),
                Boolean.TRUE.equals(request.agreedMarketing())));
        userRepository.save(newUser);
        socialAccountRepository.save(SocialAccount.create(
                newUser, pending.provider(), pending.providerUid(), email));

        return issueToken(newUser);
    }

    // 표준 회원 풀과 같은 규칙. 표준화: trim + lowercase. null 은 그대로 돌려보내 호출 측에서 거절되게 한다.
    private static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
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

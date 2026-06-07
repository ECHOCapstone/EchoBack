package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.member.service.AdminBootstrap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.transaction.annotation.Transactional;

// provider userinfo 응답을 User + SocialAccount 로 upsert 하는 공통 4분기 흐름.
// 하위 클래스는 provider 식별자와 표시명 attribute 추출만 제공한다.
//   A) (provider, sub) SocialAccount 존재 → provider_email 갱신 후 기존 User 반환
//   B) email 로 기존 User 존재 → email_verified=true 일 때만 새 SocialAccount 연결
//                                  (provider 가 검증하지 않은 email 은 표준 가입 계정의 자격증명을
//                                  도용할 수 있어 거부한다)
//   C) 둘 다 없음 → PendingSignupException — 사용자가 가입 폼에서 아이디·약관을 직접 채우게 안내한다.
//                    이메일 / nicknameHint / providerUid 를 예외에 담아 FailureHandler 가 pending 토큰으로 묶는다.
//   D) sub / email 누락 → OAuth2AuthenticationException
public abstract class AbstractOAuth2UserMapper implements OAuth2UserMapper {

    private static final String ATTR_SUB = "sub";
    private static final String ATTR_EMAIL = "email";
    private static final String ATTR_EMAIL_VERIFIED = "email_verified";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final AdminBootstrap adminBootstrap;

    protected AbstractOAuth2UserMapper(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository,
            AdminBootstrap adminBootstrap
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.adminBootstrap = adminBootstrap;
    }

    // upsert 대상 provider. SocialAccount 식별 / 생성에 쓰인다.
    protected abstract Provider provider();

    // provider 별 표시명 attribute (Google "name", Kakao "nickname"). 누락이면 null.
    protected abstract String extractDisplayName(Map<String, Object> attributes);

    @Override
    @Transactional
    public User upsert(Map<String, Object> attributes) {
        String sub = asString(attributes.get(ATTR_SUB));
        String rawEmail = asString(attributes.get(ATTR_EMAIL));

        if (sub == null || sub.isBlank()) {
            throw oauth2Error("invalid_user_info", provider() + " userinfo 응답에 sub 가 없습니다.");
        }
        if (rawEmail == null || rawEmail.isBlank()) {
            throw oauth2Error("invalid_email", provider() + " 응답에서 email 을 받지 못했습니다.");
        }
        // 표준 회원 풀과 같은 규칙으로 정규화 (case-insensitive + trim) — 같은 provider 이메일이
        // 대소문자만 다르게 들어와도 같은 사용자로 매핑되도록 한다.
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Boolean emailVerified = asBoolean(attributes.get(ATTR_EMAIL_VERIFIED));

        User user = socialAccountRepository.findByProviderAndProviderUid(provider(), sub)
                .map(existing -> {
                    // Case A — 이미 같은 provider 계정으로 로그인한 사용자.
                    existing.updateProviderEmail(email);
                    User linked = existing.getUser();
                    // 탈퇴 grace period 안에 같은 소셜 계정으로 다시 로그인 — 자동 복구.
                    if (linked.isDeleted()) {
                        linked.restore();
                    }
                    return linked;
                })
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existingUser -> {
                            // Case B — 같은 이메일의 기존 User 에 SocialAccount 만 추가.
                            // provider 가 이메일 검증을 마치지 않았다면 누구나 그 이메일로 provider
                            // 계정을 만들어 기존 표준 가입 계정의 자격을 가로챌 수 있으므로 거부한다.
                            if (!Boolean.TRUE.equals(emailVerified)) {
                                throw oauth2Error(
                                        "email_not_verified",
                                        provider() + " 가 이메일 검증을 마치지 않아 기존 계정에 자동 연결할 수 없습니다.");
                            }
                            // 탈퇴된 계정도 같은 인증 정보로의 재진입이므로 복구한다.
                            if (existingUser.isDeleted()) {
                                existingUser.restore();
                            }
                            socialAccountRepository.save(SocialAccount.create(
                                    existingUser, provider(), sub, email));
                            return existingUser;
                        })
                        .orElseThrow(() -> new PendingSignupException(
                                // Case C — 신규 사용자. 자동 가입을 막고 가입 폼으로 안내한다.
                                provider(), sub, email, extractDisplayName(attributes))));

        // 부트스트랩 관리자 계정이면 이 트랜잭션 안에서 승격한다. 인메모리 DB 처럼 부팅 시점에
        // 계정이 없던 경우, 자동 회원가입이 일어나는 로그인 시점에 바로 ADMIN 권한이 반영된다.
        adminBootstrap.promoteIfBootstrap(user);
        return user;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    // OIDC userinfo / id_token claims 의 boolean 표기는 provider 마다 Boolean 또는 "true"/"false"
    // 문자열로 도착한다. 그 외 값(예: 0/1, null)은 검증 안 됨으로 보수적으로 판단한다.
    private static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String normalized = value.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static OAuth2AuthenticationException oauth2Error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}

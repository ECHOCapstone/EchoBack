package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 카카오 OIDC userinfo 응답을 우리 User / SocialAccount 모델로 upsert 하는 단일 진입점.
// Google mapper 와 4 분기 구조는 동일하나 attribute key 는 카카오 OIDC 스펙에 맞춰
//   sub      → providerUid (pairwise — 같은 카카오 계정이라도 다른 앱이면 다른 sub)
//   email    → account_email 동의 시에만 non-null. 누락이면 invalid_email 거부 (Google 정책 일관성)
//   nickname → profile_nickname 동의 시 표시명. 누락이면 email local-part 로 fallback
//
// 분기:
//   A) (KAKAO, sub) 로 SocialAccount 존재 → access_token / provider_email 갱신, 기존 User 반환
//   B) email 로 기존 User 존재, SocialAccount 없음 → 기존 User 에 새 SocialAccount 연결
//   C) 둘 다 없음 → User.fromOAuth2 신규 + SocialAccount 신규
//   D) sub / email 누락 → OAuth2AuthenticationException (각각 invalid_user_info / invalid_email)
@Component
public class KakaoOAuth2UserMapper implements OAuth2UserMapper {

    private static final String ATTR_SUB = "sub";
    private static final String ATTR_EMAIL = "email";
    private static final String ATTR_NICKNAME = "nickname";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    public KakaoOAuth2UserMapper(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Override
    @Transactional
    public User upsert(Map<String, Object> attributes, String accessToken) {
        String sub = asString(attributes.get(ATTR_SUB));
        String email = asString(attributes.get(ATTR_EMAIL));
        String nickname = asString(attributes.get(ATTR_NICKNAME));

        if (sub == null || sub.isBlank()) {
            throw oauth2Error("invalid_user_info", "카카오 userinfo 응답에 sub 가 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw oauth2Error("invalid_email", "카카오 account_email 동의를 받지 못했습니다.");
        }

        return socialAccountRepository.findByProviderAndProviderUid(Provider.KAKAO, sub)
                .map(existing -> {
                    // Case A — 이미 같은 카카오 계정으로 로그인한 사용자.
                    existing.updateAccessToken(accessToken);
                    existing.updateProviderEmail(email);
                    return existing.getUser();
                })
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existingUser -> {
                            // Case B — 같은 이메일로 표준 가입(또는 Google) 한 사용자. SocialAccount 만 추가.
                            socialAccountRepository.save(
                                    SocialAccount.create(
                                            existingUser, Provider.KAKAO, sub, email, accessToken));
                            return existingUser;
                        })
                        .orElseGet(() -> {
                            // Case C — 신규 사용자. User + SocialAccount 동시 생성.
                            String resolvedNickname = (nickname == null || nickname.isBlank())
                                    ? localPartOf(email)
                                    : nickname;
                            User newUser = userRepository.save(User.fromOAuth2(email, resolvedNickname));
                            socialAccountRepository.save(
                                    SocialAccount.create(
                                            newUser, Provider.KAKAO, sub, email, accessToken));
                            return newUser;
                        }));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String localPartOf(String email) {
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    private static OAuth2AuthenticationException oauth2Error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}

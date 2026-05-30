package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.transaction.annotation.Transactional;

// provider userinfo 응답을 User + SocialAccount 로 upsert 하는 공통 4분기 흐름.
// 하위 클래스는 provider 식별자와 표시명 attribute 추출만 제공한다.
//   A) (provider, sub) SocialAccount 존재 → access_token / provider_email 갱신 후 기존 User 반환
//   B) email 로 기존 User 존재 → 새 SocialAccount 연결 (표준 로그인 + 소셜 로그인 방식 확장)
//   C) 둘 다 없음 → User.fromOAuth2 + SocialAccount 신규 생성
//   D) sub / email 누락 → OAuth2AuthenticationException
public abstract class AbstractOAuth2UserMapper implements OAuth2UserMapper {

    private static final String ATTR_SUB = "sub";
    private static final String ATTR_EMAIL = "email";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    protected AbstractOAuth2UserMapper(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    // upsert 대상 provider. SocialAccount 식별 / 생성에 쓰인다.
    protected abstract Provider provider();

    // provider 별 표시명 attribute (Google "name", Kakao "nickname"). 누락이면 null.
    protected abstract String extractDisplayName(Map<String, Object> attributes);

    @Override
    @Transactional
    public User upsert(Map<String, Object> attributes, String accessToken) {
        String sub = asString(attributes.get(ATTR_SUB));
        String email = asString(attributes.get(ATTR_EMAIL));

        if (sub == null || sub.isBlank()) {
            throw oauth2Error("invalid_user_info", provider() + " userinfo 응답에 sub 가 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw oauth2Error("invalid_email", provider() + " 응답에서 email 을 받지 못했습니다.");
        }

        return socialAccountRepository.findByProviderAndProviderUid(provider(), sub)
                .map(existing -> {
                    // Case A — 이미 같은 provider 계정으로 로그인한 사용자.
                    existing.updateAccessToken(accessToken);
                    existing.updateProviderEmail(email);
                    return existing.getUser();
                })
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existingUser -> {
                            // Case B — 같은 이메일의 기존 User 에 SocialAccount 만 추가.
                            socialAccountRepository.save(SocialAccount.create(
                                    existingUser, provider(), sub, email, accessToken));
                            return existingUser;
                        })
                        .orElseGet(() -> {
                            // Case C — 신규 사용자. User + SocialAccount 동시 생성.
                            User newUser = userRepository.save(
                                    User.fromOAuth2(email, resolveNickname(attributes, email)));
                            socialAccountRepository.save(SocialAccount.create(
                                    newUser, provider(), sub, email, accessToken));
                            return newUser;
                        }));
    }

    // 표시명이 없으면 이메일 local-part 를 nickname 으로 쓴다.
    private String resolveNickname(Map<String, Object> attributes, String email) {
        String displayName = extractDisplayName(attributes);
        return (displayName == null || displayName.isBlank()) ? localPartOf(email) : displayName;
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

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

// Google userinfo 응답을 우리 User / SocialAccount 모델로 upsert 하는 단일 진입점.
// Spring OAuth2 internals 와 분리되어 있어 단위 테스트 4 분기를 모두 직접 검증한다.
//
// 분기:
//   A) (GOOGLE, sub) 로 SocialAccount 존재 → access_token / provider_email 갱신, 기존 User 반환
//   B) email 로 기존 User 존재, SocialAccount 없음 → 기존 User 에 새 SocialAccount 연결
//      (CLAUDE.md "로그인 방식 확장" 요구사항 충족)
//   C) 둘 다 없음 → User.fromOAuth2 신규 + SocialAccount 신규
//   D) attributes 에 email 또는 sub 누락 → OAuth2AuthenticationException
@Component
public class GoogleOAuth2UserMapper {

    private static final String ATTR_SUB = "sub";
    private static final String ATTR_EMAIL = "email";
    private static final String ATTR_NAME = "name";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    public GoogleOAuth2UserMapper(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Transactional
    public User upsert(Map<String, Object> attributes, String accessToken) {
        String sub = asString(attributes.get(ATTR_SUB));
        String email = asString(attributes.get(ATTR_EMAIL));
        String name = asString(attributes.get(ATTR_NAME));

        if (sub == null || sub.isBlank()) {
            throw oauth2Error("invalid_user_info", "Google userinfo 응답에 sub 가 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw oauth2Error("invalid_email", "Google scope 동의 거부 등으로 email 을 받지 못했습니다.");
        }

        return socialAccountRepository.findByProviderAndProviderUid(Provider.GOOGLE, sub)
                .map(existing -> {
                    // Case A — 이미 같은 Google 계정으로 로그인한 사용자.
                    existing.updateAccessToken(accessToken);
                    existing.updateProviderEmail(email);
                    return existing.getUser();
                })
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existingUser -> {
                            // Case B — 같은 이메일로 표준 가입한 사용자. SocialAccount 만 추가.
                            socialAccountRepository.save(
                                    SocialAccount.create(
                                            existingUser, Provider.GOOGLE, sub, email, accessToken));
                            return existingUser;
                        })
                        .orElseGet(() -> {
                            // Case C — 신규 사용자. User + SocialAccount 동시 생성.
                            String nickname = (name == null || name.isBlank())
                                    ? localPartOf(email)
                                    : name;
                            User newUser = userRepository.save(User.fromOAuth2(email, nickname));
                            socialAccountRepository.save(
                                    SocialAccount.create(
                                            newUser, Provider.GOOGLE, sub, email, accessToken));
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

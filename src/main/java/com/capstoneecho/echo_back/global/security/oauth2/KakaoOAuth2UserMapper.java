package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

// 카카오 OIDC userinfo 응답 매퍼. 표시명은 profile_nickname 동의 시 내려오는 "nickname" claim 에서 가져온다.
// sub 는 pairwise 식별자라 같은 카카오 계정이라도 앱이 다르면 값이 다르다 — providerUid 로 그대로 쓴다.
@Component
public class KakaoOAuth2UserMapper extends AbstractOAuth2UserMapper {

    private static final String ATTR_NICKNAME = "nickname";

    public KakaoOAuth2UserMapper(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository
    ) {
        super(userRepository, socialAccountRepository);
    }

    @Override
    public String registrationId() {
        return "kakao";
    }

    @Override
    protected Provider provider() {
        return Provider.KAKAO;
    }

    @Override
    protected String extractDisplayName(Map<String, Object> attributes) {
        Object value = attributes.get(ATTR_NICKNAME);
        return value == null ? null : value.toString();
    }
}

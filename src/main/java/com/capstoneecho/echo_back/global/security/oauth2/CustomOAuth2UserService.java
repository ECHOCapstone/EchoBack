package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

// DefaultOAuth2UserService 를 상속해 provider userinfo 호출을 그대로 위임하고,
// registrationId 에 매칭되는 OAuth2UserMapper 로 attributes 를 넘겨 User / SocialAccount 와 동기화한다.
//
// OIDC 토글이 OFF 인 카카오처럼 id_token 이 누락된 경우 Spring 은 OIDC 경로 대신 본 클래스를 호출하므로,
// CustomOidcUserService 와 동일한 디스패처를 두어 어느 경로로 와도 매핑이 일관되게 동작한다.
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    static final String INTERNAL_USER_ID = "_internal_user_id";
    static final String INTERNAL_EMAIL = "_internal_email";
    static final String INTERNAL_USERNAME = "_internal_username";

    private final Map<String, OAuth2UserMapper> mappers;

    public CustomOAuth2UserService(
            GoogleOAuth2UserMapper googleMapper,
            KakaoOAuth2UserMapper kakaoMapper
    ) {
        this.mappers = Map.<String, OAuth2UserMapper>of(
                "google", googleMapper,
                "kakao", kakaoMapper
        );
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserMapper mapper = resolveMapper(registrationId);
        String accessToken = userRequest.getAccessToken() == null
                ? null
                : userRequest.getAccessToken().getTokenValue();

        User user = mapper.upsert(oauth2User.getAttributes(), accessToken);

        Map<String, Object> enriched = new LinkedHashMap<>(oauth2User.getAttributes());
        enriched.put(INTERNAL_USER_ID, user.getId());
        enriched.put(INTERNAL_EMAIL, user.getEmail());
        enriched.put(INTERNAL_USERNAME, user.getUsername());

        // nameAttributeKey 는 OIDC sub 를 그대로 유지해 OAuth2User.getName() 이 안정적 식별자를 반환.
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                enriched,
                "sub"
        );
    }

    // package-private — 단위 테스트가 super.loadUser HTTP 호출 없이 디스패치 로직만 검증할 수 있게 분리.
    OAuth2UserMapper resolveMapper(String registrationId) {
        OAuth2UserMapper mapper = mappers.get(registrationId);
        if (mapper == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "unsupported_provider",
                    "지원하지 않는 OAuth2 provider: " + registrationId,
                    null));
        }
        return mapper;
    }
}

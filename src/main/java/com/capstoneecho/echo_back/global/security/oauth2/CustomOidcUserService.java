package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

// openid scope 를 포함한 provider (Google, Kakao OIDC) 는 Spring Security 의 OIDC 경로를 타고
// OidcUserService 가 호출된다. registrationId 별 OAuth2UserMapper 를 골라 위임해 User /
// SocialAccount upsert 가 빠지지 않도록 한다.
//
// 신규 provider 추가:
//   1. OAuth2UserMapper 구현체를 @Component 로 등록
//   2. 본 클래스 생성자의 Map.of(...) 에 (registrationId, mapper) 한 줄 추가
@Service
public class CustomOidcUserService extends OidcUserService {

    private final Map<String, OAuth2UserMapper> mappers;

    public CustomOidcUserService(
            GoogleOAuth2UserMapper googleMapper,
            KakaoOAuth2UserMapper kakaoMapper
    ) {
        this.mappers = Map.<String, OAuth2UserMapper>of(
                "google", googleMapper,
                "kakao", kakaoMapper
        );
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserMapper mapper = resolveMapper(registrationId);
        String accessToken = userRequest.getAccessToken() == null
                ? null
                : userRequest.getAccessToken().getTokenValue();

        User user = mapper.upsert(oidcUser.getAttributes(), accessToken);

        // OidcUser.getAttributes() 는 idToken claims + userInfo claims 의 병합이다.
        // userInfo 쪽에 내부 식별자를 실어 SuccessHandler 가 동일한 key 로 꺼내 쓰게 한다.
        Map<String, Object> enriched = new LinkedHashMap<>(oidcUser.getAttributes());
        enriched.put(CustomOAuth2UserService.INTERNAL_USER_ID, user.getId());
        enriched.put(CustomOAuth2UserService.INTERNAL_EMAIL, user.getEmail());
        enriched.put(CustomOAuth2UserService.INTERNAL_USERNAME, user.getUsername());

        return new DefaultOidcUser(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                oidcUser.getIdToken(),
                new OidcUserInfo(enriched),
                "sub"
        );
    }

    // package-private — 단위 테스트가 super.loadUser HTTP 호출 없이 디스패치 로직만 검증할 수 있게 분리.
    OAuth2UserMapper resolveMapper(String registrationId) {
        OAuth2UserMapper mapper = mappers.get(registrationId);
        if (mapper == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "unsupported_provider",
                    "지원하지 않는 OIDC provider: " + registrationId,
                    null));
        }
        return mapper;
    }
}

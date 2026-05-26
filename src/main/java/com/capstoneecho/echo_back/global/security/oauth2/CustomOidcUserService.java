package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

// Google 등 openid scope 를 포함한 provider 는 Spring Security 의 OIDC 경로를 타고
// OidcUserService 가 호출된다. CustomOAuth2UserService 와 같은 mapper 호출 / attribute 보강을
// OIDC 경로에도 적용해 User / SocialAccount upsert 가 빠지지 않도록 한다.
//
// 비 OIDC provider 추가 시 (예: Kakao) 는 CustomOAuth2UserService 가 그대로 사용된다.
@Service
public class CustomOidcUserService extends OidcUserService {

    private final GoogleOAuth2UserMapper googleMapper;

    public CustomOidcUserService(GoogleOAuth2UserMapper googleMapper) {
        this.googleMapper = googleMapper;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String accessToken = userRequest.getAccessToken() == null
                ? null
                : userRequest.getAccessToken().getTokenValue();

        User user = googleMapper.upsert(oidcUser.getAttributes(), accessToken);

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
}

package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

// openid scope 를 포함한 provider (Google, Kakao OIDC) 는 Spring Security 의 OIDC 경로를 타고
// OidcUserService 가 호출된다. registrationId 별 OAuth2UserMapper 로 위임해 User / SocialAccount
// upsert 가 빠지지 않게 한다.
@Service
public class CustomOidcUserService extends OidcUserService {

    private final OAuth2UserMapperRegistry mapperRegistry;

    public CustomOidcUserService(OAuth2UserMapperRegistry mapperRegistry) {
        this.mapperRegistry = mapperRegistry;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        User user = mapperRegistry.resolve(registrationId).upsert(oidcUser.getAttributes());

        // OidcUser.getAttributes() 는 idToken claims + userInfo claims 의 병합이다.
        // userInfo 쪽에 내부 식별자를 실어 SuccessHandler 가 동일한 key 로 꺼내 쓰게 한다.
        Map<String, Object> enriched = OAuth2Attributes.enrich(oidcUser.getAttributes(), user);
        return new DefaultOidcUser(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                oidcUser.getIdToken(),
                new OidcUserInfo(enriched),
                OAuth2Attributes.NAME_ATTRIBUTE_KEY);
    }
}

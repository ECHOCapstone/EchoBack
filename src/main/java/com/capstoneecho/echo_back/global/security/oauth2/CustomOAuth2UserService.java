package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

// DefaultOAuth2UserService 를 상속해 provider userinfo 호출을 그대로 위임하고,
// registrationId 에 매칭되는 OAuth2UserMapper 로 attributes 를 넘겨 User / SocialAccount 와 동기화한다.
// OIDC 토글이 OFF 인 카카오처럼 id_token 이 누락된 경우 Spring 은 OIDC 경로 대신 본 클래스를 호출한다.
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2UserMapperRegistry mapperRegistry;

    public CustomOAuth2UserService(OAuth2UserMapperRegistry mapperRegistry) {
        this.mapperRegistry = mapperRegistry;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        User user = mapperRegistry.resolve(registrationId).upsert(oauth2User.getAttributes());

        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                OAuth2Attributes.enrich(oauth2User.getAttributes(), user),
                OAuth2Attributes.NAME_ATTRIBUTE_KEY);
    }
}

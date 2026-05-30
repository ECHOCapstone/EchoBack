package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Map;

// provider 별 OIDC / OAuth2 userinfo 응답을 우리 User + SocialAccount 도메인으로 upsert 하는 단일 진입점.
// OAuth2UserMapperRegistry 가 registrationId 로 구현체를 골라 위임한다.
public interface OAuth2UserMapper {

    // Spring Security ClientRegistration 의 registrationId ("google", "kakao" 등).
    String registrationId();

    User upsert(Map<String, Object> attributes, String accessToken);
}

package com.capstoneecho.echo_back.global.security.oauth2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

// registrationId → OAuth2UserMapper 디스패치의 단일 출처.
// 등록된 모든 OAuth2UserMapper 빈을 수집하므로, provider 추가는 매퍼 @Component 하나로 끝난다.
@Component
public class OAuth2UserMapperRegistry {

    private final Map<String, OAuth2UserMapper> byRegistrationId;

    public OAuth2UserMapperRegistry(List<OAuth2UserMapper> mappers) {
        Map<String, OAuth2UserMapper> map = new HashMap<>();
        for (OAuth2UserMapper mapper : mappers) {
            map.put(mapper.registrationId(), mapper);
        }
        this.byRegistrationId = Map.copyOf(map);
    }

    public OAuth2UserMapper resolve(String registrationId) {
        OAuth2UserMapper mapper = byRegistrationId.get(registrationId);
        if (mapper == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "unsupported_provider",
                    "지원하지 않는 OAuth2 provider: " + registrationId,
                    null));
        }
        return mapper;
    }
}

package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.LinkedHashMap;
import java.util.Map;

// OAuth2 인증 흐름이 우리 User 식별자를 OAuth2User attributes 에 실어 나르는 내부 키와 적재 로직.
// OAuth2LoginSuccessHandler 가 JWT 발급에 필요한 userId / email / username 을 같은 키로 꺼낸다.
final class OAuth2Attributes {

    static final String INTERNAL_USER_ID = "_internal_user_id";
    static final String INTERNAL_EMAIL = "_internal_email";
    static final String INTERNAL_USERNAME = "_internal_username";

    // OAuth2User.getName() 이 안정적 식별자(OIDC sub)를 반환하도록 쓰는 nameAttributeKey.
    static final String NAME_ATTRIBUTE_KEY = "sub";

    private OAuth2Attributes() {
    }

    // provider attributes 위에 내부 식별자를 덧씌운 새 맵을 만든다.
    static Map<String, Object> enrich(Map<String, Object> providerAttributes, User user) {
        Map<String, Object> enriched = new LinkedHashMap<>(providerAttributes);
        enriched.put(INTERNAL_USER_ID, user.getId());
        enriched.put(INTERNAL_EMAIL, user.getEmail());
        enriched.put(INTERNAL_USERNAME, user.getUsername());
        return enriched;
    }
}

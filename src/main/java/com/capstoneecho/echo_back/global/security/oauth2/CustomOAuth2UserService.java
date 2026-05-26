package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

// DefaultOAuth2UserService 를 상속해 Google userinfo 호출을 그대로 위임하고,
// 받은 attributes 를 GoogleOAuth2UserMapper 로 넘겨 우리 User / SocialAccount 와 동기화한다.
//
// 반환되는 DefaultOAuth2User 의 attributes 에는 후속 SuccessHandler 가 JWT 클레임으로
// 옮길 내부 식별자 (_internal_user_id, _internal_email, _internal_username) 를 함께 실어
// 컨트롤러 / 핸들러가 우리 도메인 User 를 곧장 알아볼 수 있게 한다.
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    static final String INTERNAL_USER_ID = "_internal_user_id";
    static final String INTERNAL_EMAIL = "_internal_email";
    static final String INTERNAL_USERNAME = "_internal_username";

    private final GoogleOAuth2UserMapper googleMapper;

    public CustomOAuth2UserService(GoogleOAuth2UserMapper googleMapper) {
        this.googleMapper = googleMapper;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String accessToken = userRequest.getAccessToken() == null
                ? null
                : userRequest.getAccessToken().getTokenValue();

        User user = googleMapper.upsert(oauth2User.getAttributes(), accessToken);

        Map<String, Object> enriched = new LinkedHashMap<>(oauth2User.getAttributes());
        enriched.put(INTERNAL_USER_ID, user.getId());
        enriched.put(INTERNAL_EMAIL, user.getEmail());
        enriched.put(INTERNAL_USERNAME, user.getUsername());

        // nameAttributeKey 는 Google 의 sub 를 그대로 유지해 OAuth2User.getName() 이 안정적 식별자를 반환.
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                enriched,
                "sub"
        );
    }
}

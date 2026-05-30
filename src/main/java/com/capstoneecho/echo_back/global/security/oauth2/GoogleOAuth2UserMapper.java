package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

// Google userinfo 응답 매퍼. 표시명은 OIDC 표준 "name" claim 에서 가져온다.
@Component
public class GoogleOAuth2UserMapper extends AbstractOAuth2UserMapper {

    private static final String ATTR_NAME = "name";

    public GoogleOAuth2UserMapper(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository
    ) {
        super(userRepository, socialAccountRepository);
    }

    @Override
    public String registrationId() {
        return "google";
    }

    @Override
    protected Provider provider() {
        return Provider.GOOGLE;
    }

    @Override
    protected String extractDisplayName(Map<String, Object> attributes) {
        Object value = attributes.get(ATTR_NAME);
        return value == null ? null : value.toString();
    }
}

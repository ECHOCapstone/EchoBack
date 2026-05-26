package com.capstoneecho.echo_back.global.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher.MatchResult;
import org.springframework.stereotype.Component;

// 프로젝트 내 다른 인증 경로 (`/api/auth/...`) 와 일관성을 위해 OAuth2 시작 endpoint 를
// Spring 기본 `/oauth2/authorization/{registrationId}` 가 아니라
// `/api/auth/oauth2/{registrationId}/authorization` 으로 노출한다.
//
// DefaultOAuth2AuthorizationRequestResolver 는 패턴 `<baseUri>/{registrationId}` 가 하드코딩이라
// registrationId 뒤에 suffix 를 둘 수 없다. AntPathRequestMatcher 로 우리 패턴을 직접 매칭하고
// registrationId 만 추출해 default resolver 의 resolve(req, registrationId) 에 위임하면,
// state 생성 / scope 처리 / redirect URI 빌드 등 복잡한 로직은 그대로 재사용할 수 있다.
@Component
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String PATTERN = "/api/auth/oauth2/{registrationId}/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final PathPatternRequestMatcher matcher =
            PathPatternRequestMatcher.withDefaults().matcher(PATTERN);

    public CustomOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        // delegate.baseUri 는 임의값 — 우리는 resolve(req, registrationId) 만 호출하므로
        // 그쪽 matcher 가 어떤 패턴이든 무관하다.
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        MatchResult result = matcher.matcher(request);
        if (!result.isMatch()) {
            return null;
        }
        String registrationId = result.getVariables().get("registrationId");
        if (registrationId == null || registrationId.isBlank()) {
            return null;
        }
        return delegate.resolve(request, registrationId);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return delegate.resolve(request, registrationId);
    }
}

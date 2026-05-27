package com.capstoneecho.echo_back.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

// CustomOidcUserService 의 디스패치 로직(registrationId → OAuth2UserMapper) 만 검증한다.
// 실제 OIDC HTTP 흐름(super.loadUser) 은 통합 환경에서만 의미가 있으므로 단위 테스트에서는 제외.
// CustomOAuth2UserService 도 동일 디스패치 구조이므로 본 테스트가 표준 회귀로 함께 동작한다.
@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private GoogleOAuth2UserMapper googleMapper;
    private KakaoOAuth2UserMapper kakaoMapper;

    private CustomOidcUserService oidcService;
    private CustomOAuth2UserService oauth2Service;

    @BeforeEach
    void setUp() {
        googleMapper = new GoogleOAuth2UserMapper(userRepository, socialAccountRepository);
        kakaoMapper = new KakaoOAuth2UserMapper(userRepository, socialAccountRepository);
        oidcService = new CustomOidcUserService(googleMapper, kakaoMapper);
        oauth2Service = new CustomOAuth2UserService(googleMapper, kakaoMapper);
    }

    @Test
    @DisplayName("OIDC: registrationId=google → GoogleOAuth2UserMapper 반환")
    void oidcResolvesGoogleMapper() {
        assertThat(oidcService.resolveMapper("google")).isSameAs(googleMapper);
    }

    @Test
    @DisplayName("OIDC: registrationId=kakao → KakaoOAuth2UserMapper 반환")
    void oidcResolvesKakaoMapper() {
        assertThat(oidcService.resolveMapper("kakao")).isSameAs(kakaoMapper);
    }

    @Test
    @DisplayName("OIDC: 미지원 registrationId → unsupported_provider OAuth2AuthenticationException")
    void oidcRejectsUnsupportedProvider() {
        assertThatThrownBy(() -> oidcService.resolveMapper("naver"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("unsupported_provider"));
    }

    @Test
    @DisplayName("OAuth2 (non-OIDC): registrationId=google → GoogleOAuth2UserMapper 반환")
    void oauth2ResolvesGoogleMapper() {
        assertThat(oauth2Service.resolveMapper("google")).isSameAs(googleMapper);
    }

    @Test
    @DisplayName("OAuth2 (non-OIDC): registrationId=kakao → KakaoOAuth2UserMapper 반환")
    void oauth2ResolvesKakaoMapper() {
        assertThat(oauth2Service.resolveMapper("kakao")).isSameAs(kakaoMapper);
    }

    @Test
    @DisplayName("OAuth2 (non-OIDC): 미지원 registrationId → unsupported_provider OAuth2AuthenticationException")
    void oauth2RejectsUnsupportedProvider() {
        assertThatThrownBy(() -> oauth2Service.resolveMapper("naver"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("unsupported_provider"));
    }
}

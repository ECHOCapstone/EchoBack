package com.capstoneecho.echo_back.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

// registrationId → OAuth2UserMapper 디스패치(단일 출처)를 검증한다.
// CustomOidcUserService / CustomOAuth2UserService 가 공유하므로 본 테스트가 양쪽 회귀를 함께 보장한다.
@ExtendWith(MockitoExtension.class)
class OAuth2UserMapperRegistryTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private GoogleOAuth2UserMapper googleMapper;
    private KakaoOAuth2UserMapper kakaoMapper;
    private OAuth2UserMapperRegistry registry;

    @BeforeEach
    void setUp() {
        googleMapper = new GoogleOAuth2UserMapper(userRepository, socialAccountRepository);
        kakaoMapper = new KakaoOAuth2UserMapper(userRepository, socialAccountRepository);
        registry = new OAuth2UserMapperRegistry(List.of(googleMapper, kakaoMapper));
    }

    @Test
    @DisplayName("registrationId=google → GoogleOAuth2UserMapper 반환")
    void resolvesGoogleMapper() {
        assertThat(registry.resolve("google")).isSameAs(googleMapper);
    }

    @Test
    @DisplayName("registrationId=kakao → KakaoOAuth2UserMapper 반환")
    void resolvesKakaoMapper() {
        assertThat(registry.resolve("kakao")).isSameAs(kakaoMapper);
    }

    @Test
    @DisplayName("미지원 registrationId → unsupported_provider OAuth2AuthenticationException")
    void rejectsUnsupportedProvider() {
        assertThatThrownBy(() -> registry.resolve("naver"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("unsupported_provider"));
    }
}

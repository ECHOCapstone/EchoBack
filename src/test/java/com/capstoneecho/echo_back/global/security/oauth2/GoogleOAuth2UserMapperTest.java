package com.capstoneecho.echo_back.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.member.service.AdminBootstrap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2UserMapperTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private AdminBootstrap adminBootstrap;

    private GoogleOAuth2UserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GoogleOAuth2UserMapper(userRepository, socialAccountRepository, adminBootstrap);
    }

    private static Map<String, Object> googleAttrs(String sub, String email, String name) {
        return Map.of("sub", sub, "email", email, "name", name);
    }

    @Test
    @DisplayName("Case A — 이미 (GOOGLE, sub) 로 SocialAccount 가 있으면 토큰만 갱신하고 기존 User 를 반환한다")
    void caseAExistingSocialAccountUpdatesToken() {
        User existingUser = User.fromOAuth2("alice@gmail.com", "Alice");
        SocialAccount existingAccount = SocialAccount.create(
                existingUser, Provider.GOOGLE, "sub-123", "alice@gmail.com", "old-token");
        when(socialAccountRepository.findByProviderAndProviderUid(Provider.GOOGLE, "sub-123"))
                .thenReturn(Optional.of(existingAccount));

        User result = mapper.upsert(
                googleAttrs("sub-123", "alice@gmail.com", "Alice"), "new-token");

        assertThat(result).isSameAs(existingUser);
        assertThat(existingAccount.getAccessToken()).isEqualTo("new-token");
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case B — 같은 이메일로 표준 가입한 User 가 있으면 새 SocialAccount 만 연결한다")
    void caseBExistingUserGetsNewSocialAccount() {
        User existingUser = User.fromOAuth2("alice@gmail.com", "Alice");
        when(socialAccountRepository.findByProviderAndProviderUid(Provider.GOOGLE, "sub-new"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@gmail.com"))
                .thenReturn(Optional.of(existingUser));

        User result = mapper.upsert(
                googleAttrs("sub-new", "alice@gmail.com", "Google Alice"), "tok-1");

        assertThat(result).isSameAs(existingUser);
        verify(userRepository, never()).save(any());
        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository, times(1)).save(captor.capture());
        SocialAccount saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(existingUser);
        assertThat(saved.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(saved.getProviderUid()).isEqualTo("sub-new");
        assertThat(saved.getProviderEmail()).isEqualTo("alice@gmail.com");
        assertThat(saved.getAccessToken()).isEqualTo("tok-1");
    }

    @Test
    @DisplayName("Case C — User/SocialAccount 둘 다 없으면 자동 가입하지 않고 PendingSignupException 을 던진다")
    void caseCBrandNewUserThrowsPendingSignup() {
        when(socialAccountRepository.findByProviderAndProviderUid(Provider.GOOGLE, "sub-x"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@gmail.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mapper.upsert(
                googleAttrs("sub-x", "new@gmail.com", "Newbie"), "tok-c"))
                .isInstanceOf(PendingSignupException.class)
                .satisfies(ex -> {
                    PendingSignupException pending = (PendingSignupException) ex;
                    assertThat(pending.provider()).isEqualTo(Provider.GOOGLE);
                    assertThat(pending.providerUid()).isEqualTo("sub-x");
                    assertThat(pending.email()).isEqualTo("new@gmail.com");
                    assertThat(pending.nicknameHint()).isEqualTo("Newbie");
                });

        verify(userRepository, never()).save(any(User.class));
        verify(socialAccountRepository, never()).save(any(SocialAccount.class));
    }

    @Test
    @DisplayName("Case C — name 이 비어 있으면 nicknameHint 도 null 로 두고 가입 폼에서 사용자가 직접 정한다")
    void caseCPendingSignupKeepsNicknameHintNullWhenNameMissing() {
        when(socialAccountRepository.findByProviderAndProviderUid(eq(Provider.GOOGLE), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mapper.upsert(
                Map.of("sub", "sub-noname", "email", "lonely@gmail.com"), "tok"))
                .isInstanceOf(PendingSignupException.class)
                .satisfies(ex -> assertThat(((PendingSignupException) ex).nicknameHint()).isNull());
    }

    @Test
    @DisplayName("Case D — sub 가 누락되면 invalid_user_info OAuth2AuthenticationException")
    void caseDMissingSubThrows() {
        assertThatThrownBy(() -> mapper.upsert(
                Map.of("email", "x@gmail.com"), "tok"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("invalid_user_info"));

        verify(userRepository, never()).findByEmail(any());
        verify(socialAccountRepository, never()).findByProviderAndProviderUid(any(), any());
    }

    @Test
    @DisplayName("Case D — email 이 누락되면 invalid_email OAuth2AuthenticationException")
    void caseDMissingEmailThrows() {
        assertThatThrownBy(() -> mapper.upsert(
                Map.of("sub", "sub-1"), "tok"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("invalid_email"));
    }
}

package com.capstoneecho.echo_back.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialAccountTest {

    private static User newUser() {
        return User.fromOAuth2("alice@example.com", "Alice");
    }

    @Test
    @DisplayName("create 는 모든 필드를 채우고 createdAt 을 현재 시각으로 설정한다")
    void createPopulatesAllFieldsAndCreatedAt() {
        User user = newUser();

        SocialAccount account = SocialAccount.create(
                user, Provider.GOOGLE, "sub-123", "alice@example.com");

        assertThat(account.getUser()).isSameAs(user);
        assertThat(account.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(account.getProviderUid()).isEqualTo("sub-123");
        assertThat(account.getProviderEmail()).isEqualTo("alice@example.com");
        assertThat(account.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create 는 user 가 null 이면 IllegalArgumentException 을 던진다")
    void createRejectsNullUser() {
        assertThatThrownBy(() ->
                SocialAccount.create(null, Provider.GOOGLE, "sub-1", "a@b.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user");
    }

    @Test
    @DisplayName("create 는 provider 가 null 이면 IllegalArgumentException 을 던진다")
    void createRejectsNullProvider() {
        assertThatThrownBy(() ->
                SocialAccount.create(newUser(), null, "sub-1", "a@b.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    @Test
    @DisplayName("create 는 providerUid 가 비어 있으면 IllegalArgumentException 을 던진다")
    void createRejectsBlankProviderUid() {
        assertThatThrownBy(() ->
                SocialAccount.create(newUser(), Provider.GOOGLE, " ", "a@b.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerUid");
        assertThatThrownBy(() ->
                SocialAccount.create(newUser(), Provider.GOOGLE, null, "a@b.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerUid");
    }

    @Test
    @DisplayName("updateProviderEmail 은 새로운 이메일로 교체하지만 blank 입력은 무시한다")
    void updateProviderEmailReplacesValueButIgnoresBlank() {
        SocialAccount account = SocialAccount.create(
                newUser(), Provider.GOOGLE, "sub-1", "old@b.com");

        account.updateProviderEmail("new@b.com");
        assertThat(account.getProviderEmail()).isEqualTo("new@b.com");

        account.updateProviderEmail("");
        assertThat(account.getProviderEmail()).isEqualTo("new@b.com");

        account.updateProviderEmail(null);
        assertThat(account.getProviderEmail()).isEqualTo("new@b.com");
    }
}

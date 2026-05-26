package com.capstoneecho.echo_back.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 User 가 여러 OAuth provider 와 연결될 수 있도록 별도 1:N 모델.
// CLAUDE.md 의 "동일 이메일 표준 사용자에게 OAuth 로그인 방식을 확장" 요구사항을
// User 본체 변경 없이 social link 추가만으로 충족한다.
@Entity
@Table(
        name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_social_accounts_provider_uid",
                        columnNames = {"provider", "provider_uid"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private Provider provider;

    // Google 의 경우 OpenID Connect 의 sub claim. provider 별로 형식이 달라 충분히 길게.
    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    // Google access_token 은 보통 200~300 자. 갱신될 수 있으므로 mutable 컬럼.
    @Column(name = "access_token", length = 2048)
    private String accessToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private SocialAccount(
            User user,
            Provider provider,
            String providerUid,
            String providerEmail,
            String accessToken
    ) {
        this.user = user;
        this.provider = provider;
        this.providerUid = providerUid;
        this.providerEmail = providerEmail;
        this.accessToken = accessToken;
        this.createdAt = Instant.now();
    }

    public static SocialAccount create(
            User user,
            Provider provider,
            String providerUid,
            String providerEmail,
            String accessToken
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        if (providerUid == null || providerUid.isBlank()) {
            throw new IllegalArgumentException("providerUid is required");
        }
        return new SocialAccount(user, provider, providerUid, providerEmail, accessToken);
    }

    // 재로그인 시 Google 이 발급하는 새 access_token 으로 갱신한다.
    public void updateAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    // 사용자가 Google 프로필 email 을 바꾼 경우 (드물지만 가능) 반영.
    public void updateProviderEmail(String providerEmail) {
        if (providerEmail == null || providerEmail.isBlank()) {
            return;
        }
        this.providerEmail = providerEmail;
    }
}

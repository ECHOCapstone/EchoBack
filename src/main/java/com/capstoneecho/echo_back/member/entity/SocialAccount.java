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
//
// access_token 은 provider API 재호출 경로가 없어 보관하지 않는다 (V13 에서 컬럼 제거).
// provider 식별과 자동 로그인은 (provider, provider_uid) 의 조합만으로 충분하다.
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

    // provider 가 알려준 사용자 email — 표시 / 어드민 식별용. User.email 과는 별개로 관리한다.
    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private SocialAccount(
            User user,
            Provider provider,
            String providerUid,
            String providerEmail
    ) {
        this.user = user;
        this.provider = provider;
        this.providerUid = providerUid;
        this.providerEmail = providerEmail;
        this.createdAt = Instant.now();
    }

    public static SocialAccount create(
            User user,
            Provider provider,
            String providerUid,
            String providerEmail
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
        return new SocialAccount(user, provider, providerUid, providerEmail);
    }

    // 사용자가 provider 프로필 email 을 바꾼 경우 (드물지만 가능) 반영. blank 입력은 무시.
    public void updateProviderEmail(String providerEmail) {
        if (providerEmail == null || providerEmail.isBlank()) {
            return;
        }
        this.providerEmail = providerEmail;
    }
}

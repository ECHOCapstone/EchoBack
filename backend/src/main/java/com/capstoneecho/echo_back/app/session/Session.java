package com.capstoneecho.echo_back.app.session;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 사용자가 직접 대본을 입력해 학습하는 맞춤 세션. scriptText 가 채워지면 녹음/피드백 단계로 진입할 수 있다.
@Entity
@Table(name = "sessions", indexes = @Index(name = "ix_sessions_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(name = "script_text", columnDefinition = "TEXT", nullable = false)
    private String scriptText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Session create(Long userId, String title) {
        var s = new Session();
        s.userId = userId;
        s.title = title;
        s.scriptText = "";
        return s;
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void rename(String title) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
    }

    public void updateScript(String scriptText) {
        if (scriptText != null) {
            this.scriptText = scriptText;
        }
    }
}

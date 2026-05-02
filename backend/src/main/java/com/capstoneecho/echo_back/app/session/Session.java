package com.capstoneecho.echo_back.app.session;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    // 사용자 학습 목록 정렬 시 즐겨찾기를 먼저 노출하기 위한 플래그.
    @Column(nullable = false)
    private boolean favorite;

    // 사용자가 한 호흡으로 발음할 학습 단위. 분할 정책 자체는 외부(SessionService + SentenceSplitter)
    // 가 결정하고, 본 컬렉션은 그 결과를 영속화한다. orphanRemoval 로 갱신 시 이전 항목을 청소한다.
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sentenceIndex ASC")
    private List<SessionSentence> sentences = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Session create(Long userId, String title) {
        var s = new Session();
        s.userId = userId;
        s.title = title;
        s.scriptText = "";
        s.favorite = false;
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

    // 즐겨찾기 토글의 단일 진입점. SessionUpdateRequest 에서 favorite 필드가 들어올 때만 호출된다.
    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    // 새 대본 텍스트 + 분할된 문장 리스트를 한 번에 반영한다. 도메인 자신은 분할 정책을 모르고
    // 단지 결과만 받는다 (DIP). 입력이 null 이면 변경하지 않는다.
    public void updateScript(String scriptText, List<String> sentenceTexts) {
        if (scriptText == null) return;
        this.scriptText = scriptText;
        this.sentences.clear();
        if (sentenceTexts != null) {
            int idx = 0;
            for (var text : sentenceTexts) {
                this.sentences.add(SessionSentence.of(this, idx++, text));
            }
        }
    }
}

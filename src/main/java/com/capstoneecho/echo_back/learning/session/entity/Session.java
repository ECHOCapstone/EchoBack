package com.capstoneecho.echo_back.learning.session.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
// 사용자가 직접 대본을 입력해 만드는 맞춤 학습 세션. scriptText 가 채워지면 녹음/피드백으로 넘어간다.
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

    // 목록을 즐겨찾기 우선으로 정렬할 때 쓰는 플래그.
    @Column(nullable = false)
    private boolean favorite;

    // 분할된 문장들. SentenceSplitter 결과를 그대로 받아 보관하고, 갱신 시 이전 항목은 orphanRemoval 로 정리된다.
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

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    // 새 대본과 분할된 문장 리스트를 한 번에 반영한다. scriptText 가 null 이면 그대로 둔다.
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

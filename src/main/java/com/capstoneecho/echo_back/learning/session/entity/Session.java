package com.capstoneecho.echo_back.learning.session.entity;

import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.member.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "script_text", nullable = false, columnDefinition = "TEXT")
    private String scriptText;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sentenceIndex ASC")
    private List<SessionSentence> sentences = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Session(User user, String title) {
        this.user = user;
        this.title = title;
        this.scriptText = "";
        this.favorite = false;
    }

    public static Session create(User user, String title) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        return new Session(user, title);
    }

    public void rename(String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            return;
        }
        this.title = newTitle;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void updateScript(String newScriptText, SentenceSplitter sentenceSplitter) {
        if (newScriptText == null) {
            return;
        }
        if (sentenceSplitter == null) {
            throw new IllegalArgumentException("sentenceSplitter is required");
        }
        this.scriptText = newScriptText;
        this.sentences.clear();
        int idx = 0;
        for (String text : sentenceSplitter.split(newScriptText)) {
            this.sentences.add(SessionSentence.of(this, idx++, text));
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

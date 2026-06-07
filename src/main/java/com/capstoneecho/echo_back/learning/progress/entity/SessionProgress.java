package com.capstoneecho.echo_back.learning.progress.entity;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.member.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 맞춤 학습 세션의 진행 상태. ChapterProgress 와 같은 정책을 sentence 단위에 적용한다.
@Entity
@Table(
        name = "session_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_progress_user_session",
                columnNames = {"user_id", "session_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "last_completed_sentence_index", nullable = false)
    private int lastCompletedSentenceIndex;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private SessionProgress(User user, Session session) {
        this.user = user;
        this.session = session;
        this.lastCompletedSentenceIndex = -1;
    }

    public static SessionProgress start(User user, Session session) {
        if (user == null || session == null) {
            throw new IllegalArgumentException("user and session are required");
        }
        return new SessionProgress(user, session);
    }

    // 음수 인덱스는 도메인 위반 — BusinessException(INVALID_REQUEST) 로 알려 400 매핑되게 한다.
    public void advanceTo(int sentenceIndex) {
        if (sentenceIndex < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "sentenceIndex must be non-negative");
        }
        if (sentenceIndex > this.lastCompletedSentenceIndex) {
            this.lastCompletedSentenceIndex = sentenceIndex;
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.startedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

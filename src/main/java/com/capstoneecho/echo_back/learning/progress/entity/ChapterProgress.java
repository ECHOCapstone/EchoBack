package com.capstoneecho.echo_back.learning.progress.entity;

import com.capstoneecho.echo_back.learning.script.entity.Script;
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

// 한 사용자가 한 챕터(script) 의 어디까지 학습했는지 보존한다.
// lastCompletedStepIndex 는 0-based 인덱스 — -1 이면 아직 어떤 step 도 완료하지 않은 상태.
// (user_id, script_id) 가 unique 라 한 챕터에는 한 진행 상태만 존재한다.
@Entity
@Table(
        name = "chapter_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chapter_progress_user_script",
                columnNames = {"user_id", "script_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChapterProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @Column(name = "last_completed_step_index", nullable = false)
    private int lastCompletedStepIndex;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ChapterProgress(User user, Script script) {
        this.user = user;
        this.script = script;
        this.lastCompletedStepIndex = -1;
    }

    // 새 진행 상태 생성. 처음엔 어떤 step 도 완료하지 않은 -1 상태에서 시작한다.
    public static ChapterProgress start(User user, Script script) {
        if (user == null || script == null) {
            throw new IllegalArgumentException("user and script are required");
        }
        return new ChapterProgress(user, script);
    }

    // 새 step 완료를 반영. 과거 인덱스는 무시하고 더 큰 값일 때만 갱신한다 — 사용자가 중간 step 으로
    // 되돌아가도 진행률은 뒤로 가지 않는다.
    public void advanceTo(int stepIndex) {
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must be non-negative");
        }
        if (stepIndex > this.lastCompletedStepIndex) {
            this.lastCompletedStepIndex = stepIndex;
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

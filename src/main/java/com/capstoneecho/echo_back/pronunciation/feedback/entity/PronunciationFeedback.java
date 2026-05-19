package com.capstoneecho.echo_back.pronunciation.feedback.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 한 챕터 또는 세션을 끝냈을 때 만들어지는 종합 피드백 한 건.
// accuracy 는 step 점수 평균(0~100), weakPhoneme 는 가장 자주 틀린 음소.
@Entity
@Table(name = "pronunciation_feedbacks", indexes = {
        @Index(name = "ix_feedbacks_user", columnList = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PronunciationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "script_id")
    private Long scriptId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(nullable = false)
    private double accuracy;

    @Column(name = "weak_phoneme", length = 32)
    private String weakPhoneme;

    @Column(name = "practice_word", length = 100)
    private String practiceWord;

    @Column(name = "guidance_kr", columnDefinition = "TEXT")
    private String guidanceKr;

    // 보상이 한 번 적용된 피드백인지. complete 가 두 번 호출돼도 EXP 가 중복 가산되지 않도록 막는다.
    @Column(nullable = false)
    private boolean completed;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PhonemeError> errors = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PronunciationFeedback create(
            Long userId,
            Long scriptId,
            Long sessionId,
            String title,
            double accuracy,
            String weakPhoneme,
            String practiceWord,
            String guidanceKr
    ) {
        var f = new PronunciationFeedback();
        f.userId = userId;
        f.scriptId = scriptId;
        f.sessionId = sessionId;
        f.title = title;
        f.accuracy = accuracy;
        f.weakPhoneme = weakPhoneme;
        f.practiceWord = practiceWord;
        f.guidanceKr = guidanceKr;
        return f;
    }

    public void addError(PhonemeError error) {
        error.attachTo(this);
        this.errors.add(error);
    }

    // 처음 완료 처리할 때만 true 를 반환한다. 호출자는 이 값으로 보상 가산 여부를 결정한다.
    public boolean markCompleted() {
        if (completed) return false;
        this.completed = true;
        return true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}

package com.capstoneecho.echo_back.pronunciation.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 PronunciationFeedback 에 누적되는 retry-word 시도 한 건의 분석 스냅샷.
// 같은 feedback 에서 사용자가 동일 단어 / 구를 여러 번 재시도하면 모두 INSERT 되어
// LLM 재시도 컨텍스트의 priorAttempts 로 다시 묶여 들어간다.
@Entity
@Table(
        name = "retry_attempts",
        indexes = @Index(
                name = "ix_retry_attempts_feedback_created",
                columnList = "feedback_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private PronunciationFeedback feedback;

    @Column(name = "word", nullable = false, length = 200)
    private String word;

    @Column(name = "perceived", columnDefinition = "TEXT")
    private String perceived;

    @Column(name = "canonical", columnDefinition = "TEXT")
    private String canonical;

    @Column(name = "errors_json", columnDefinition = "TEXT")
    private String errorsJson;

    @Column(name = "score")
    private Double score;

    @Column(name = "guidance_kr", columnDefinition = "TEXT")
    private String guidanceKr;

    @Column(name = "correct", nullable = false)
    private boolean correct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RetryAttempt(
            PronunciationFeedback feedback,
            String word,
            String perceived,
            String canonical,
            String errorsJson,
            Double score,
            String guidanceKr,
            boolean correct) {
        this.feedback = feedback;
        this.word = word;
        this.perceived = perceived;
        this.canonical = canonical;
        this.errorsJson = errorsJson;
        this.score = score;
        this.guidanceKr = guidanceKr;
        this.correct = correct;
    }

    public static RetryAttempt create(
            PronunciationFeedback feedback,
            String word,
            String perceived,
            String canonical,
            String errorsJson,
            Double score,
            String guidanceKr,
            boolean correct) {
        if (feedback == null) {
            throw new IllegalArgumentException("feedback is required");
        }
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("word is required");
        }
        return new RetryAttempt(
                feedback, word, perceived, canonical, errorsJson, score, guidanceKr, correct);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}

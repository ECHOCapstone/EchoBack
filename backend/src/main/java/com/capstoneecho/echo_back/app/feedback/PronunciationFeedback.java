package com.capstoneecho.echo_back.app.feedback;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 한 unit/세션 학습 종료 시 생성되는 종합 피드백.
// accuracy 는 step 점수 평균(0~100), weakPhoneme 는 가장 빈번한 오류 음소,
// guidanceKr 은 LLM(또는 규칙 기반) 이 작성한 한국어 안내문, practiceWord 는 재연습 권장 단어.
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

    // 학습 완료 보상이 적용되었는지를 표시한다. true 가 되는 순간이 EXP/streak 가산이 일어난 시점이고,
    // 이후 같은 피드백으로 complete 가 다시 호출돼도 보상이 중복되지 않도록 도메인 가드 역할을 한다.
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

    // 보상 적용 시점을 기록하는 단일 진입점. 호출자는 반환값으로 실제 적용 여부를 판단한다.
    //   true  → 첫 호출이라 보상 적용을 진행해야 함
    //   false → 이미 완료된 피드백이므로 보상 가산 없이 idempotent 응답으로 마무리해야 함
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

package com.capstoneecho.echo_back.pronunciation.feedback.entity;

import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.member.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "pronunciation_feedbacks",
        indexes = @Index(
                name = "idx_feedback_user_completed_at",
                columnList = "user_id, completed_at"
        )
)
@Check(
        name = "ck_pronunciation_feedbacks_script_session_xor",
        constraints =
                "(script_id IS NULL AND session_id IS NULL)"
                        + " OR ((script_id IS NULL) <> (session_id IS NULL))"
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PronunciationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Session session;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "accuracy", nullable = false)
    private double accuracy;

    @Column(name = "weak_phoneme", length = 32)
    private String weakPhoneme;

    @Column(name = "practice_word", length = 100)
    private String practiceWord;

    @Column(name = "guidance_kr", columnDefinition = "TEXT")
    private String guidanceKr;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "task_title", length = 200)
    private String taskTitle;

    @Column(name = "script_text", columnDefinition = "TEXT")
    private String scriptText;

    // LLM 종합 피드백의 strengths / weaknesses / nextPracticeItems 를 JSON 으로 캐싱한다.
    // 저장 포맷: {"strengths":[...], "weaknesses":[...], "nextPracticeItems":[{text,kind,reason}, ...]}
    @Column(name = "comprehensive_json", columnDefinition = "TEXT")
    private String comprehensiveJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PhonemeError> errors = new ArrayList<>();

    private PronunciationFeedback(
            User user,
            Script script,
            Session session,
            String title,
            double accuracy,
            String weakPhoneme,
            String practiceWord,
            String guidanceKr,
            String taskTitle,
            String scriptText
    ) {
        this.user = user;
        this.script = script;
        this.session = session;
        this.title = title;
        this.accuracy = accuracy;
        this.weakPhoneme = weakPhoneme;
        this.practiceWord = practiceWord;
        this.guidanceKr = guidanceKr;
        this.taskTitle = taskTitle;
        this.scriptText = scriptText;
        this.completed = false;
    }

    public static PronunciationFeedback forScript(
            User user,
            Script script,
            String title,
            double accuracy,
            String weakPhoneme,
            String practiceWord,
            String guidanceKr
    ) {
        requireNonNull(user, "user");
        requireNonNull(script, "script");
        requireNonBlank(title, "title");
        requireAccuracyRange(accuracy);
        return new PronunciationFeedback(
                user, script, null, title, accuracy, weakPhoneme, practiceWord, guidanceKr,
                null, null);
    }

    public static PronunciationFeedback forSession(
            User user,
            Session session,
            String title,
            double accuracy,
            String weakPhoneme,
            String practiceWord,
            String guidanceKr
    ) {
        requireNonNull(user, "user");
        requireNonNull(session, "session");
        requireNonBlank(title, "title");
        requireAccuracyRange(accuracy);
        if (session.getUser() != user) {
            throw new IllegalArgumentException("session.user must equal the supplied user");
        }
        return new PronunciationFeedback(
                user, null, session, title, accuracy, weakPhoneme, practiceWord, guidanceKr,
                session.getTitle(), session.getScriptText());
    }

    public void attach(PhonemeError phonemeError) {
        if (phonemeError == null) {
            throw new IllegalArgumentException("phonemeError is required");
        }
        phonemeError.attachTo(this);
        this.errors.add(phonemeError);
    }

    public PhonemeError recordPhonemeError(
            PhonemeOp op, String canonical, String perceived, Integer canonicalIndex) {
        PhonemeError error = PhonemeError.create(op, canonical, perceived, canonicalIndex);
        attach(error);
        return error;
    }

    public void updateGuidance(String newGuidance) {
        if (newGuidance == null || newGuidance.isBlank()) {
            return;
        }
        this.guidanceKr = newGuidance;
    }

    // LLM 종합 결과 JSON 을 통째로 캐싱한다. 빈 입력은 NULL 로 남긴다.
    public void applyComprehensiveJson(String json) {
        this.comprehensiveJson = (json == null || json.isBlank()) ? null : json;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void requireAccuracyRange(double accuracy) {
        if (accuracy < 0.0 || accuracy > 100.0) {
            throw new IllegalArgumentException("accuracy must be in [0, 100]");
        }
    }
}

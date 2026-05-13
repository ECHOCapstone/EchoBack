package com.capstoneecho.echo_back.pronunciation.recording.entity;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "recordings")
@Check(
        name = "ck_recordings_script_session_xor",
        constraints =
                "(script_id IS NULL AND session_id IS NULL)"
                        + " OR ((script_id IS NULL) <> (session_id IS NULL))"
)
@Check(
        name = "ck_recordings_step_requires_script",
        constraints = "step_id IS NULL OR script_id IS NOT NULL"
)
@Check(
        name = "ck_recordings_sentence_requires_session",
        constraints = "session_sentence_id IS NULL OR session_id IS NOT NULL"
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private LearningStep step;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_sentence_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private SessionSentence sessionSentence;

    @Column(name = "audio_path", nullable = false, length = 500)
    private String audioPath;

    @Column(name = "target_text_snapshot", columnDefinition = "TEXT")
    private String targetTextSnapshot;

    @Column(name = "duration_sec")
    private Double durationSec;

    @Column(name = "perceived", columnDefinition = "TEXT")
    private String perceived;

    @Column(name = "canonical", columnDefinition = "TEXT")
    private String canonical;

    @Column(name = "peak_softmax", columnDefinition = "TEXT")
    private String peakSoftmax;

    @Column(name = "errors_json", columnDefinition = "TEXT")
    private String errorsJson;

    @Column(name = "step_score")
    private Double stepScore;

    @Column(name = "guidance_kr", columnDefinition = "TEXT")
    private String guidanceKr;

    @Column(name = "wrong_words_json", columnDefinition = "TEXT")
    private String wrongWordsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Recording(
            User user,
            Script script,
            LearningStep step,
            Session session,
            SessionSentence sessionSentence,
            String audioPath,
            String targetTextSnapshot
    ) {
        this.user = user;
        this.script = script;
        this.step = step;
        this.session = session;
        this.sessionSentence = sessionSentence;
        this.audioPath = audioPath;
        this.targetTextSnapshot = targetTextSnapshot;
    }

    public static Recording forScriptStep(
            User user,
            Script script,
            LearningStep step,
            String audioPath,
            String targetTextSnapshot
    ) {
        requireNonNull(user, "user");
        requireNonNull(script, "script");
        requireNonNull(step, "step");
        requireNonBlank(audioPath, "audioPath");
        if (step.getScript() != script) {
            throw new IllegalArgumentException("step.script must equal the supplied script");
        }
        return new Recording(user, script, step, null, null, audioPath, targetTextSnapshot);
    }

    public static Recording forSessionSentence(
            User user,
            Session session,
            SessionSentence sentence,
            String audioPath,
            String targetTextSnapshot
    ) {
        requireNonNull(user, "user");
        requireNonNull(session, "session");
        requireNonNull(sentence, "sentence");
        requireNonBlank(audioPath, "audioPath");
        if (sentence.getSession() != session) {
            throw new IllegalArgumentException("sentence.session must equal the supplied session");
        }
        if (session.getUser() != user) {
            throw new IllegalArgumentException("session.user must equal the supplied user");
        }
        return new Recording(user, null, null, session, sentence, audioPath, targetTextSnapshot);
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
}

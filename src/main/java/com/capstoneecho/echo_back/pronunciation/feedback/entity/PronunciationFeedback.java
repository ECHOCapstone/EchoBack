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
import jakarta.persistence.UniqueConstraint;
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
        ),
        // 같은 (user, 녹음 집합) 으로 generate 를 반복 호출해도 피드백이 한 row 만 생기도록 막는다.
        // recording_ids_hash 는 scriptId/sessionId + 정렬된 recordingIds 를 SHA-256 한 값. 이게 없으면
        // generate 를 N 번 찍어 N 개 피드백을 각각 complete 해 경험치를 복제할 수 있다 (멱등성 가드).
        // 기존 row(해시 NULL)는 유니크 인덱스에서 NULL 이 서로 distinct 로 취급돼 충돌하지 않는다.
        uniqueConstraints = @UniqueConstraint(
                name = "uq_feedback_user_recording_hash",
                columnNames = {"user_id", "recording_ids_hash"}
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

    // 부모 콘텐츠가 삭제되어도 학습자의 발음 기록은 보존한다 (SET NULL).
    // title / accuracy / weak_phoneme 등 denormalized 스냅샷이 살아 있어 히스토리 재구성이 가능하다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
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

    // generate 멱등 키. nullable — 이 컬럼 도입(V24) 이전에 만들어진 row 는 NULL 로 남는다.
    // 새로 생성되는 피드백은 generate 가 항상 채우며, (user_id, recording_ids_hash) 유니크 제약이 중복을 막는다.
    @Column(name = "recording_ids_hash", length = 64)
    private String recordingIdsHash;

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

    // generate 멱등 키를 부여한다. 영속화 직전 한 번만 호출되며, 이후 (user_id, recording_ids_hash)
    // 유니크 제약이 같은 녹음 집합으로 만든 중복 피드백을 DB 레벨에서 거부한다.
    public void assignRecordingIdsHash(String hash) {
        this.recordingIdsHash = (hash == null || hash.isBlank()) ? null : hash;
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

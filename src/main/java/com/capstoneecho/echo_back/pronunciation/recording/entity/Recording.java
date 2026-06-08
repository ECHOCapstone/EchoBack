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

    // 부모 콘텐츠가 삭제되어도 학습자의 녹음과 target_text_snapshot / canonical / perceived 스냅샷은 살린다.
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
        // 영속화 전 객체는 ID 가 모두 null 이므로 reference 동일성으로 검증한다.
        // 영속 엔티티/프록시는 ID 비교로 검증해 EntityManager.getReference 로 받은 프록시도 통과한다.
        if (!sameParent(step.getScript(), script)) {
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
        if (!sameParent(sentence.getSession(), session)) {
            throw new IllegalArgumentException("sentence.session must equal the supplied session");
        }
        if (!sameParent(session.getUser(), user)) {
            throw new IllegalArgumentException("session.user must equal the supplied user");
        }
        return new Recording(user, null, null, session, sentence, audioPath, targetTextSnapshot);
    }

    // reference 동일성 또는 영속 ID 동일성으로 부모를 비교한다.
    private static boolean sameParent(Object linked, Object expected) {
        if (linked == expected) {
            return true;
        }
        if (linked == null || expected == null) {
            return false;
        }
        Long a = identifierOf(linked);
        Long b = identifierOf(expected);
        return a != null && a.equals(b);
    }

    private static Long identifierOf(Object entity) {
        if (entity instanceof User u) return u.getId();
        if (entity instanceof Script s) return s.getId();
        if (entity instanceof Session s) return s.getId();
        return null;
    }

    // 호출 측이 READ TX 에서 부모 관계를 이미 검증한 경우의 가벼운 팩토리.
    // 부모 엔티티는 EntityManager.getReference 로 받은 프록시여도 되며, 추가 로드를 일으키지 않는다.
    public static Recording forScriptStepUnchecked(
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
        return new Recording(user, script, step, null, null, audioPath, targetTextSnapshot);
    }

    public static Recording forSessionSentenceUnchecked(
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
        return new Recording(user, null, null, session, sentence, audioPath, targetTextSnapshot);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // wrongWords JSON 캐시를 채운다. NULL / 빈 문자열 입력은 컬럼을 NULL 로 남긴다.
    // 저장 포맷: [{"word":"...","index":0}, ...].
    public void applyWrongWordsJson(String json) {
        this.wrongWordsJson = (json == null || json.isBlank()) ? null : json;
    }

    // 모델 서버의 음소 오류 목록을 JSON 으로 직렬화해 저장한다.
    // 종합 피드백 단계에서 챕터 전체 약점 음소 빈도를 계산할 때 다시 역직렬화한다.
    // 저장 포맷: [{"op":"SUB","canonical":"R","perceived":"L","canonicalIndex":3}, ...].
    public void applyErrorsJson(String json) {
        this.errorsJson = (json == null || json.isBlank()) ? null : json;
    }

    // 모델 서버가 돌려준 perceived / canonical / peakSoftmax 음소 시퀀스를 캐시한다.
    // 단순 String 으로 저장 (공백 구분). 종합 단계에서 누적 컨텍스트 구성용.
    public void applyAnalysisSnapshot(String perceived, String canonical, String peakSoftmax) {
        this.perceived = blankToNull(perceived);
        this.canonical = blankToNull(canonical);
        this.peakSoftmax = blankToNull(peakSoftmax);
    }

    // step 점수를 저장한다 (0~100). null 입력은 모르는 점수로 남긴다.
    public void applyStepScore(Double score) {
        this.stepScore = score;
    }

    // LLM 이 만든 한국어 가이던스를 저장한다. 빈 입력은 NULL 로 남긴다.
    public void applyGuidanceKr(String guidanceKr) {
        this.guidanceKr = blankToNull(guidanceKr);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
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

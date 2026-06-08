package com.capstoneecho.echo_back.pronunciation.recording.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.support.DataJpaMySqlTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DataJpaMySqlTest
class RecordingCheckConstraintTest {

    @Autowired
    private EntityManager em;

    private record Fixture(Long userId, Long scriptId, Long stepId, Long sessionId) {}

    private Fixture seed() {
        User user = User.fromOAuth2("ck@example.com", "CK");
        em.persist(user);

        Track track = Track.create("Track", "desc", 1);
        em.persist(track);
        Script script = Script.createChapter(track, 1, "Ch", "content", Difficulty.EASY, null, null);
        em.persist(script);
        LearningStep step = LearningStep.record(script, "p", "t");
        em.persist(step);

        Session session = Session.create(user, "T");
        em.persist(session);

        em.flush();
        return new Fixture(user.getId(), script.getId(), step.getId(), session.getId());
    }

    private int insertRecording(Long userId, Long scriptId, Long stepId,
                                 Long sessionId, Long sessionSentenceId) {
        return em.createNativeQuery(
                        "INSERT INTO recordings "
                                + "(user_id, script_id, step_id, session_id, session_sentence_id, "
                                + "audio_path, created_at) "
                                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, CURRENT_TIMESTAMP)")
                .setParameter(1, userId)
                .setParameter(2, scriptId)
                .setParameter(3, stepId)
                .setParameter(4, sessionId)
                .setParameter(5, sessionSentenceId)
                .setParameter(6, "/tmp/x.wav")
                .executeUpdate();
    }

    @Test
    @DisplayName("script_id 와 session_id 가 동시에 NOT NULL 인 raw INSERT 는 CHECK 위반")
    void rawInsertWithBothParentsViolatesCheck() {
        Fixture f = seed();

        assertThatThrownBy(() -> {
            insertRecording(f.userId(), f.scriptId(), null, f.sessionId(), null);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("step_id 만 NOT NULL 이고 script_id 가 NULL 인 raw INSERT 는 CHECK 위반")
    void rawInsertWithStepWithoutScriptViolatesCheck() {
        Fixture f = seed();

        assertThatThrownBy(() -> {
            insertRecording(f.userId(), null, f.stepId(), f.sessionId(), null);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }
}

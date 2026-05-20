package com.capstoneecho.echo_back.pronunciation.feedback.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.member.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedbackFactoryTest {

    private static final String TITLE = "Chapter 1 Feedback";
    private static final double ACCURACY = 87.5;
    private static final String WEAK = "TH";
    private static final String PRACTICE = "think";
    private static final String GUIDANCE = "혀를 살짝 내밀어 발음하세요.";

    private User user(String email) {
        return User.fromOAuth2(email, "Nick");
    }

    private Script chapterScript(String title) {
        Track track = Track.create("Track", "desc", 1);
        return Script.createChapter(track, 1, title, "content here.", Difficulty.EASY, null, null);
    }

    @Test
    @DisplayName("forScript 은 user/script 를 세팅하고 session 은 NULL, snapshot 도 비어 있다")
    void forScriptPopulatesScriptModeOnly() {
        User user = user("u1@example.com");
        Script script = chapterScript("S1");

        PronunciationFeedback fb = PronunciationFeedback.forScript(
                user, script, TITLE, ACCURACY, WEAK, PRACTICE, GUIDANCE);

        assertThat(fb.getUser()).isSameAs(user);
        assertThat(fb.getScript()).isSameAs(script);
        assertThat(fb.getSession()).isNull();
        assertThat(fb.getTitle()).isEqualTo(TITLE);
        assertThat(fb.getAccuracy()).isEqualTo(ACCURACY);
        assertThat(fb.getWeakPhoneme()).isEqualTo(WEAK);
        assertThat(fb.getPracticeWord()).isEqualTo(PRACTICE);
        assertThat(fb.getGuidanceKr()).isEqualTo(GUIDANCE);
        assertThat(fb.isCompleted()).isFalse();
        assertThat(fb.getCompletedAt()).isNull();
        assertThat(fb.getTaskTitle()).isNull();
        assertThat(fb.getScriptText()).isNull();
        assertThat(fb.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("forScript 은 필수 인자 null/blank 와 잘못된 accuracy 를 거부 (cross-parent invariant)")
    void forScriptRejectsCrossParent() {
        User user = user("u2@example.com");
        Script script = chapterScript("S2");

        assertThatThrownBy(() ->
                PronunciationFeedback.forScript(null, script, TITLE, ACCURACY, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PronunciationFeedback.forScript(user, null, TITLE, ACCURACY, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PronunciationFeedback.forScript(user, script, " ", ACCURACY, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PronunciationFeedback.forScript(user, script, TITLE, -0.1, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PronunciationFeedback.forScript(user, script, TITLE, 100.1, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("forSession 은 session 을 세팅하고 script 는 NULL, 세션 스냅샷(title/scriptText)을 보존한다")
    void forSessionPopulatesSessionModeAndSnapshot() {
        User user = user("u3@example.com");
        Session session = Session.create(user, "MyTalk");

        PronunciationFeedback fb = PronunciationFeedback.forSession(
                user, session, TITLE, ACCURACY, WEAK, PRACTICE, GUIDANCE);

        assertThat(fb.getUser()).isSameAs(user);
        assertThat(fb.getScript()).isNull();
        assertThat(fb.getSession()).isSameAs(session);
        assertThat(fb.getTaskTitle()).isEqualTo("MyTalk");
        assertThat(fb.getScriptText()).isEqualTo(session.getScriptText());
    }

    @Test
    @DisplayName("forSession 은 session.user 가 인자 user 와 다르면 거부 (cross-parent)")
    void forSessionRejectsCrossParent() {
        User owner = user("owner@example.com");
        User intruder = user("intruder@example.com");
        Session session = Session.create(owner, "OwnerTalk");

        assertThatThrownBy(() ->
                PronunciationFeedback.forSession(
                        intruder, session, TITLE, ACCURACY, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("forSession 은 필수 인자 null/blank 를 거부")
    void forSessionRejectsNullArguments() {
        User user = user("u4@example.com");
        Session session = Session.create(user, "T");

        assertThatThrownBy(() ->
                PronunciationFeedback.forSession(null, session, TITLE, ACCURACY, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PronunciationFeedback.forSession(user, null, TITLE, ACCURACY, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PronunciationFeedback.forSession(user, session, "", ACCURACY, WEAK, PRACTICE, GUIDANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

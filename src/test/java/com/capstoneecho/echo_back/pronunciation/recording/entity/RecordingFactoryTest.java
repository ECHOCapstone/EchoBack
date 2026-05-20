package com.capstoneecho.echo_back.pronunciation.recording.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.support.DefaultSentenceSplitter;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
import com.capstoneecho.echo_back.pronunciation.recording.dto.WrongWord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RecordingFactoryTest {

    private static final DefaultSentenceSplitter SPLITTER = new DefaultSentenceSplitter();
    private static final String AUDIO_PATH = "/tmp/recording.wav";
    private static final String SNAPSHOT = "Hello world.";

    private User user(String email) {
        return User.fromOAuth2(email, "Nick");
    }

    private Script chapterScript(String title) {
        Track track = Track.create("Track", "desc", 1);
        return Script.createChapter(track, 1, title, "content here.", Difficulty.EASY, null, null);
    }

    @Test
    @DisplayName("forScriptStep 은 user/script/step 을 세팅하고 session/sentence 는 NULL")
    void scriptStepFactoryPopulatesScriptModeOnly() {
        User user = user("u1@example.com");
        Script script = chapterScript("S1");
        LearningStep step = LearningStep.record(script, "say it", "Hello world.");

        Recording recording = Recording.forScriptStep(user, script, step, AUDIO_PATH, SNAPSHOT);

        assertThat(recording.getUser()).isSameAs(user);
        assertThat(recording.getScript()).isSameAs(script);
        assertThat(recording.getStep()).isSameAs(step);
        assertThat(recording.getSession()).isNull();
        assertThat(recording.getSessionSentence()).isNull();
        assertThat(recording.getAudioPath()).isEqualTo(AUDIO_PATH);
        assertThat(recording.getTargetTextSnapshot()).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("forScriptStep 은 step.script 가 인자 script 와 다르면 거부 (cross-parent)")
    void scriptStepFactoryRejectsStepFromOtherScript() {
        User user = user("u2@example.com");
        Script scriptA = chapterScript("ScriptA");
        Script scriptB = chapterScript("ScriptB");
        LearningStep stepOfB = LearningStep.record(scriptB, "p", "t");

        assertThatThrownBy(() ->
                Recording.forScriptStep(user, scriptA, stepOfB, AUDIO_PATH, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("forScriptStep 은 필수 인자 null/blank 를 거부")
    void scriptStepFactoryRejectsNullArguments() {
        User user = user("u3@example.com");
        Script script = chapterScript("S");
        LearningStep step = LearningStep.record(script, "p", "t");

        assertThatThrownBy(() -> Recording.forScriptStep(null, script, step, AUDIO_PATH, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recording.forScriptStep(user, null, step, AUDIO_PATH, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recording.forScriptStep(user, script, null, AUDIO_PATH, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recording.forScriptStep(user, script, step, " ", SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("forSessionSentence 은 session/sentence 를 세팅하고 script/step 는 NULL")
    void sessionSentenceFactoryPopulatesSessionMode() {
        User user = user("u4@example.com");
        Session session = Session.create(user, "Title");
        session.updateScript("Hello world. Foo bar.", SPLITTER);
        SessionSentence sentence = session.getSentences().get(0);

        Recording recording = Recording.forSessionSentence(user, session, sentence, AUDIO_PATH, SNAPSHOT);

        assertThat(recording.getUser()).isSameAs(user);
        assertThat(recording.getScript()).isNull();
        assertThat(recording.getStep()).isNull();
        assertThat(recording.getSession()).isSameAs(session);
        assertThat(recording.getSessionSentence()).isSameAs(sentence);
    }

    @Test
    @DisplayName("forSessionSentence 는 sentence.session 이 인자 session 과 다르면 거부 (cross-parent)")
    void sessionSentenceFactoryRejectsCrossParent() {
        User user = user("u5@example.com");
        Session sessionA = Session.create(user, "A");
        sessionA.updateScript("Hello world. Foo bar.", SPLITTER);
        Session sessionB = Session.create(user, "B");
        sessionB.updateScript("Other one. Two.", SPLITTER);
        SessionSentence sentenceOfA = sessionA.getSentences().get(0);

        assertThatThrownBy(() ->
                Recording.forSessionSentence(user, sessionB, sentenceOfA, AUDIO_PATH, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("forSessionSentence 는 session.user 가 인자 user 와 다르면 거부")
    void sessionSentenceFactoryRejectsSessionFromOtherUser() {
        User owner = user("owner@example.com");
        User intruder = user("intruder@example.com");
        Session session = Session.create(owner, "T");
        session.updateScript("One. Two.", SPLITTER);
        SessionSentence sentence = session.getSentences().get(0);

        assertThatThrownBy(() ->
                Recording.forSessionSentence(intruder, session, sentence, AUDIO_PATH, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("applyWrongWordsJson + RecordingUploadResponse.from: WrongWord[] round-trip")
    void wrongWordsJsonRoundTripsThroughResponse() {
        ObjectMapper mapper = JsonMapper.builder().build();
        User user = user("rt@example.com");
        Script script = chapterScript("RT");
        LearningStep step = LearningStep.record(script, "say", "Hello world.");
        Recording recording = Recording.forScriptStep(user, script, step, AUDIO_PATH, SNAPSHOT);

        recording.applyWrongWordsJson("[{\"word\":\"water\",\"index\":0}]");

        RecordingUploadResponse response = RecordingUploadResponse.from(recording, mapper);
        assertThat(response.wrongWords())
                .containsExactly(new WrongWord("water", 0));
    }

    @Test
    @DisplayName("RecordingUploadResponse.from: wrongWordsJson NULL → [] 폴백")
    void fromRecordingFallsBackToEmptyWhenJsonNull() {
        ObjectMapper mapper = JsonMapper.builder().build();
        User user = user("rtnull@example.com");
        Script script = chapterScript("RTN");
        LearningStep step = LearningStep.record(script, "say", "Hello.");
        Recording recording = Recording.forScriptStep(user, script, step, AUDIO_PATH, SNAPSHOT);

        RecordingUploadResponse response = RecordingUploadResponse.from(recording, mapper);

        assertThat(response.wrongWords()).isEmpty();
    }

    @Test
    @DisplayName("RecordingUploadResponse.from: 손상된 JSON → [] 폴백 + WARN 로그")
    void fromRecordingFallsBackToEmptyWhenJsonMalformed() {
        ObjectMapper mapper = JsonMapper.builder().build();
        User user = user("rtbad@example.com");
        Script script = chapterScript("RTB");
        LearningStep step = LearningStep.record(script, "say", "Hello.");
        Recording recording = Recording.forScriptStep(user, script, step, AUDIO_PATH, SNAPSHOT);
        recording.applyWrongWordsJson("not-json-at-all");

        RecordingUploadResponse response = RecordingUploadResponse.from(recording, mapper);

        assertThat(response.wrongWords()).isEmpty();
    }

    @Test
    @DisplayName("applyWrongWordsJson 빈 문자열 → NULL 로 정규화")
    void applyWrongWordsJsonBlankNormalizesToNull() {
        User user = user("blank@example.com");
        Script script = chapterScript("B");
        LearningStep step = LearningStep.record(script, "say", "Hi.");
        Recording recording = Recording.forScriptStep(user, script, step, AUDIO_PATH, SNAPSHOT);

        recording.applyWrongWordsJson("   ");
        assertThat(recording.getWrongWordsJson()).isNull();

        recording.applyWrongWordsJson(null);
        assertThat(recording.getWrongWordsJson()).isNull();
    }

}

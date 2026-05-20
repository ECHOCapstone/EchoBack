package com.capstoneecho.echo_back.pronunciation.feedback.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class FeedbackRepositoryDataJpaTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private EntityManager em;

    private Script persistScript(String title) {
        Track track = Track.create("Track", "desc", 1);
        em.persist(track);
        Script script = Script.createChapter(track, 1, title, "content here.", Difficulty.EASY, null, null);
        em.persist(script);
        return script;
    }

    private User persistUser(String email) {
        User user = User.fromOAuth2(email, "Nick");
        em.persist(user);
        return user;
    }

    @Test
    @DisplayName("markCompletedAtomically 는 첫 호출에 1, 같은 행 두번째 호출에 0 을 반환한다")
    void markCompletedAtomicallyReturnsOneFirstTimeZeroAfter() {
        User user = persistUser("a@example.com");
        Script script = persistScript("S1");
        PronunciationFeedback fb = feedbackRepository.saveAndFlush(
                PronunciationFeedback.forScript(user, script, "T1", 80.0, "TH", "think", "guide"));

        Instant now = Instant.parse("2026-05-12T12:34:56Z");

        int first = feedbackRepository.markCompletedAtomically(fb.getId(), user.getId(), now);
        em.flush();
        em.clear();

        int second = feedbackRepository.markCompletedAtomically(fb.getId(), user.getId(), now);
        em.flush();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();

        PronunciationFeedback reloaded = feedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(reloaded.isCompleted()).isTrue();
        assertThat(reloaded.getCompletedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("markCompletedAtomically 는 타인 userId 로는 0 을 반환하고 행을 수정하지 않는다")
    void markCompletedAtomicallyRejectsForeignUser() {
        User owner = persistUser("owner@example.com");
        User other = persistUser("other@example.com");
        Script script = persistScript("S2");
        PronunciationFeedback fb = feedbackRepository.saveAndFlush(
                PronunciationFeedback.forScript(owner, script, "T2", 70.0, null, null, null));

        int affected = feedbackRepository.markCompletedAtomically(
                fb.getId(), other.getId(), Instant.parse("2026-05-12T00:00:00Z"));
        em.flush();
        em.clear();

        assertThat(affected).isZero();
        PronunciationFeedback reloaded = feedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(reloaded.isCompleted()).isFalse();
        assertThat(reloaded.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("findByIdAndUser_Id 는 소유자 본인 행만 반환한다")
    void findByIdAndUserScopedToOwner() {
        User owner = persistUser("o@example.com");
        User other = persistUser("x@example.com");
        Script script = persistScript("S3");
        PronunciationFeedback fb = feedbackRepository.saveAndFlush(
                PronunciationFeedback.forScript(owner, script, "T3", 90.0, null, null, null));

        Optional<PronunciationFeedback> found =
                feedbackRepository.findByIdAndUser_Id(fb.getId(), owner.getId());
        Optional<PronunciationFeedback> denied =
                feedbackRepository.findByIdAndUser_Id(fb.getId(), other.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("T3");
        assertThat(denied).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser_IdOrderByCompletedAtDesc 는 completedAt DESC 순으로 정렬된다")
    void findAllByUserOrderedByCompletedAtDesc() {
        User user = persistUser("u@example.com");
        Script script = persistScript("S4");

        PronunciationFeedback older = feedbackRepository.saveAndFlush(
                PronunciationFeedback.forScript(user, script, "Older", 80.0, null, null, null));
        PronunciationFeedback newer = feedbackRepository.saveAndFlush(
                PronunciationFeedback.forScript(user, script, "Newer", 85.0, null, null, null));

        feedbackRepository.markCompletedAtomically(
                older.getId(), user.getId(), Instant.parse("2026-05-10T00:00:00Z"));
        feedbackRepository.markCompletedAtomically(
                newer.getId(), user.getId(), Instant.parse("2026-05-11T00:00:00Z"));
        em.flush();
        em.clear();

        List<PronunciationFeedback> rows =
                feedbackRepository.findAllByUser_IdOrderByCompletedAtDesc(user.getId());

        assertThat(rows).extracting(PronunciationFeedback::getId)
                .containsExactly(newer.getId(), older.getId());
    }
}

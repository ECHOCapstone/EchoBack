package com.capstoneecho.echo_back.pronunciation.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class FeedbackServiceConcurrencyTest {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("#7 complete 가 두 스레드에서 동시에 호출돼도 EXP 는 정확히 한 번만 가산된다")
    void completeCalledTwiceAddsExpExactlyOnce() throws Exception {
        runComplete(2);
    }

    @Test
    @DisplayName("#7 추가 — 동일 feedbackId 를 4 스레드 동시 호출 시 EXP 가 정확히 1회만 가산된다")
    void completeIsIdempotentUnderConcurrency() throws Exception {
        runComplete(4);
    }

    private void runComplete(int threads) throws Exception {
        User user = transactionTemplate.execute(status ->
                userRepository.save(
                        User.fromOAuth2(
                                "concurrent-" + System.nanoTime() + "@example.com", "Conc")));

        Script script = transactionTemplate.execute(status -> {
            Track track = trackRepository.save(Track.create("ConcTrack-" + System.nanoTime(), "d", 1));
            return scriptRepository.save(
                    Script.createChapter(
                            track, 1, "ConcScript", "content", Difficulty.EASY, null, null));
        });

        PronunciationFeedback fb = transactionTemplate.execute(status ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, "T", 80.0, "TH", "think", "guide")));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        feedbackService.complete(user.getId(), fb.getId());
                        successes.incrementAndGet();
                    } catch (BusinessException ex) {
                        failures.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getExp())
                .as("EXP should be awarded exactly once across %s concurrent complete() calls",
                        threads)
                .isEqualTo(FeedbackService.DEFAULT_COMPLETION_EXP);

        PronunciationFeedback reloadedFb =
                feedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(reloadedFb.isCompleted()).isTrue();
        assertThat(reloadedFb.getCompletedAt()).isNotNull();
    }
}

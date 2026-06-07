package com.capstoneecho.echo_back.statistics.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.statistics.ranking.dto.RankingResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// 주간 랭킹 정책 검증. 활동 임계 통과 / 미통과, Top N 컷, 동일 점수 tie-breaker 등을 격리해서 확인한다.
// 매 테스트마다 SettingsService 오버라이드로 windowDays / topN / minActivity 를 고정해서
// 기본값 변화에 흔들리지 않게 한다.
@SpringBootTest
@ActiveProfiles("test")
class RankingServiceTest {

    private static final String WINDOW = RuntimeSettings.WEEKLY_WINDOW_DAYS;
    private static final String TOP_N = RuntimeSettings.WEEKLY_TOP_N;
    private static final String MIN_ACTIVITY = RuntimeSettings.RANKING_MIN_ACTIVITY_COUNT;

    @Autowired private RankingService rankingService;
    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppProperties appProperties;
    @Autowired private SettingsService settingsService;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetState() {
        transactionTemplate.executeWithoutResult(s -> feedbackRepository.deleteAll());
        settingsService.set(WINDOW, "7");
        settingsService.set(TOP_N, "5");
        settingsService.set(MIN_ACTIVITY, "3");
    }

    @Test
    @DisplayName("활동 임계 미통과자는 entries 에서 제외되고 myRank=0 + 활동 정보만 노출된다")
    void belowThresholdExcludedButOwnActivityStillReported() {
        User user = newUser("below");
        Script script = seedScript("below");
        completeFeedbackAt(user, script, "S1", 95.0, Instant.now());
        completeFeedbackAt(user, script, "S2", 92.0, Instant.now());

        RankingResponse response = rankingService.weekly(user.getId());

        assertThat(response.minActivityCount()).isEqualTo(3);
        assertThat(response.totalRanked()).isZero();
        assertThat(response.entries()).isEmpty();
        assertThat(response.myRank()).isZero();
        assertThat(response.myActivityCount()).isEqualTo(2);
        assertThat(response.myAccuracy()).isEqualTo(93.5);
        assertThat(response.myEntryShown()).isFalse();
    }

    @Test
    @DisplayName("임계 통과자는 entries 에 포함되고 myEntryShown=true, 평균 정확도가 정렬 기준이 된다")
    void thresholdPassedIncludesSelfWithAverage() {
        User user = newUser("pass");
        Script script = seedScript("pass");
        Instant now = Instant.now();
        completeFeedbackAt(user, script, "S1", 80.0, now);
        completeFeedbackAt(user, script, "S2", 90.0, now);
        completeFeedbackAt(user, script, "S3", 100.0, now);

        RankingResponse response = rankingService.weekly(user.getId());

        assertThat(response.totalRanked()).isEqualTo(1);
        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).isMe()).isTrue();
        assertThat(response.entries().get(0).activityCount()).isEqualTo(3);
        assertThat(response.entries().get(0).accuracy()).isEqualTo(90.0);
        assertThat(response.myRank()).isEqualTo(1);
        assertThat(response.myAccuracy()).isEqualTo(90.0);
        assertThat(response.myEntryShown()).isTrue();
    }

    @Test
    @DisplayName("평균 정확도 내림차순 정렬 — 임계를 모두 통과한 여러 사용자")
    void sortsByAverageAccuracyDescending() {
        Script script = seedScript("sort");
        Instant now = Instant.now();

        User high = newUser("high");
        completeFeedbackAt(high, script, "h1", 95.0, now);
        completeFeedbackAt(high, script, "h2", 90.0, now);
        completeFeedbackAt(high, script, "h3", 100.0, now); // avg 95

        User mid = newUser("mid");
        completeFeedbackAt(mid, script, "m1", 80.0, now);
        completeFeedbackAt(mid, script, "m2", 70.0, now);
        completeFeedbackAt(mid, script, "m3", 90.0, now);  // avg 80

        User viewer = newUser("viewer");
        completeFeedbackAt(viewer, script, "v1", 60.0, now);
        completeFeedbackAt(viewer, script, "v2", 70.0, now);
        completeFeedbackAt(viewer, script, "v3", 65.0, now); // avg 65

        RankingResponse response = rankingService.weekly(viewer.getId());

        assertThat(response.entries())
                .extracting(RankingResponse.Entry::accuracy)
                .isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(response.entries().get(0).rank()).isEqualTo(1);
        assertThat(response.entries().get(0).accuracy()).isEqualTo(95.0);
        assertThat(response.myRank()).isEqualTo(3);
    }

    @Test
    @DisplayName("동일 평균이면 활동 횟수가 더 많은 쪽이 위로 — 꾸준함을 보상하는 tie-breaker")
    void tieBreakerOnActivityCount() {
        Script script = seedScript("tie");
        Instant now = Instant.now();

        User fewerButPerfect = newUser("few");
        completeFeedbackAt(fewerButPerfect, script, "f1", 90.0, now);
        completeFeedbackAt(fewerButPerfect, script, "f2", 90.0, now);
        completeFeedbackAt(fewerButPerfect, script, "f3", 90.0, now);

        User moreSameAverage = newUser("more");
        for (int i = 0; i < 6; i++) {
            completeFeedbackAt(moreSameAverage, script, "m" + i, 90.0, now);
        }

        RankingResponse response = rankingService.weekly(moreSameAverage.getId());

        assertThat(response.entries().get(0).nickname()).isEqualTo(moreSameAverage.getNickname());
        assertThat(response.entries().get(0).activityCount()).isEqualTo(6);
        assertThat(response.entries().get(1).activityCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Top N 컷 밖이라도 본인이 임계를 통과했다면 myRank / myAccuracy 가 정확히 보고된다")
    void selfOutsideTopButQualified() {
        settingsService.set(TOP_N, "2");

        Script script = seedScript("out");
        Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            User u = newUser("top" + i);
            // 90, 85, 80, 75, 70 평균
            double base = 90.0 - i * 5.0;
            completeFeedbackAt(u, script, "t" + i + "a", base, now);
            completeFeedbackAt(u, script, "t" + i + "b", base, now);
            completeFeedbackAt(u, script, "t" + i + "c", base, now);
        }

        User viewer = newUser("low");
        completeFeedbackAt(viewer, script, "l1", 60.0, now);
        completeFeedbackAt(viewer, script, "l2", 60.0, now);
        completeFeedbackAt(viewer, script, "l3", 60.0, now);

        RankingResponse response = rankingService.weekly(viewer.getId());

        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries()).noneMatch(RankingResponse.Entry::isMe);
        assertThat(response.totalRanked()).isEqualTo(6);
        assertThat(response.myRank()).isEqualTo(6);
        assertThat(response.myAccuracy()).isEqualTo(60.0);
        assertThat(response.myEntryShown()).isFalse();
    }

    @Test
    @DisplayName("응답 period 는 \"M/d ~ M/d\" 형식 + windowDays 가 그대로 노출된다")
    void exposesPeriodAndWindow() {
        User user = newUser("period");

        RankingResponse response = rankingService.weekly(user.getId());

        assertThat(response.windowDays()).isEqualTo(7);
        assertThat(response.period()).matches("\\d{1,2}/\\d{1,2} ~ \\d{1,2}/\\d{1,2}");
        assertThat(appProperties.gamification().rankingMinActivityCount()).isEqualTo(3);
    }

    private Script seedScript(String suffix) {
        return transactionTemplate.execute(s -> {
            Track track = trackRepository.save(
                    Track.create("T-ranking-" + suffix + "-" + System.nanoTime(), "d", 1));
            return scriptRepository.save(
                    Script.createChapter(track, 1, "Script-" + suffix,
                            "content", Difficulty.EASY, null, null));
        });
    }

    private User newUser(String localPart) {
        return transactionTemplate.execute(s -> userRepository.save(User.signup(
                localPart + System.nanoTime(),
                localPart + "-" + System.nanoTime() + "@test.com",
                passwordEncoder.encode("Password1!"),
                "Nick" + localPart)));
    }

    private void completeFeedbackAt(User user, Script script, String title, double accuracy, Instant completedAt) {
        Long fbId = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, title, accuracy, "TH", "think", "g")).getId());
        int affected = transactionTemplate.execute(s ->
                feedbackRepository.markCompletedAtomically(fbId, user.getId(), completedAt));
        assertThat(affected).isEqualTo(1);
    }
}

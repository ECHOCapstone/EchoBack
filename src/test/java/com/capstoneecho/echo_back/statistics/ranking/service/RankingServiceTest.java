package com.capstoneecho.echo_back.statistics.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.global.config.AppProperties;
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
import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
import com.capstoneecho.echo_back.statistics.ranking.repository.DemoRankingEntryRepository;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class RankingServiceTest {

    @Autowired private RankingService rankingService;
    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private DemoRankingEntryRepository demoRankingEntryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppProperties appProperties;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seedDemoRanking() {
        transactionTemplate.executeWithoutResult(s -> {
            feedbackRepository.deleteAll();
            demoRankingEntryRepository.deleteAll();
            demoRankingEntryRepository.save(DemoRankingEntry.create("Aaron", 95));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Brenda", 88));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Cody", 81));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Dana", 74));
            demoRankingEntryRepository.save(DemoRankingEntry.create("Erin", 67));
        });
    }

    @Test
    @DisplayName("sortsByAccuracyDescending: demo 시드와 사용자 정확도가 accuracy 내림차순으로 정렬된다")
    void sortsByAccuracyDescending() {
        User user = newUser("sortdesc");

        RankingResponse response = rankingService.today(user.getId());

        assertThat(response.entries())
                .extracting(RankingResponse.Entry::accuracy)
                .isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(response.entries().get(0).rank()).isEqualTo(1);
        assertThat(response.entries().get(0).accuracy()).isEqualTo(95.0);
    }

    @Test
    @DisplayName("includesSelfWhenUserHasTodayFeedback: 본인이 오늘 완료된 피드백을 가지면 entries 에 isMe=true 로 포함")
    void includesSelfWhenUserHasTodayFeedback() {
        User user = newUser("withfb");
        Script script = seedScript("withfb");
        completeFeedbackAt(user, script, "My Today", 90.0, Instant.now());

        RankingResponse response = rankingService.today(user.getId());

        assertThat(response.myRank()).isGreaterThan(0);
        assertThat(response.myAccuracy()).isEqualTo(90.0);
        assertThat(response.entries())
                .anySatisfy(e -> {
                    assertThat(e.isMe()).isTrue();
                    assertThat(e.accuracy()).isEqualTo(90.0);
                    assertThat(e.nickname()).isEqualTo(user.getNickname());
                });
        assertThat(response.totalUsers()).isGreaterThanOrEqualTo(6);
        assertThat(response.unitTitle()).isNotBlank();
    }

    @Test
    @DisplayName("markSelfUnrankedWhenNoTodayFeedback: 오늘 피드백이 없으면 myRank=0, myAccuracy=0, isMe entry 없음")
    void markSelfUnrankedWhenNoTodayFeedback() {
        User user = newUser("nofb");

        RankingResponse response = rankingService.today(user.getId());

        assertThat(response.myRank()).isEqualTo(0);
        assertThat(response.myAccuracy()).isEqualTo(0.0);
        assertThat(response.entries()).noneMatch(RankingResponse.Entry::isMe);
        assertThat(response.entries()).hasSize(5);
    }

    @Test
    @DisplayName("사용자별 best accuracy 만 채택 (동일 사용자 다중 피드백)")
    void aggregatesBestAccuracyPerUser() {
        User user = newUser("best");
        Script script = seedScript("best");
        Instant now = Instant.now();
        completeFeedbackAt(user, script, "Run 1", 65.0, now);
        completeFeedbackAt(user, script, "Run 2", 92.0, now);
        completeFeedbackAt(user, script, "Run 3", 78.0, now);

        RankingResponse response = rankingService.today(user.getId());

        long selfMatches = response.entries().stream()
                .filter(RankingResponse.Entry::isMe)
                .count();
        assertThat(selfMatches).isEqualTo(1);
        assertThat(response.myAccuracy()).isEqualTo(92.0);
    }

    @SuppressWarnings("unused")
    private ZoneId kst() {
        return ZoneId.of(appProperties.stats().zone());
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

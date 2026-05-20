package com.capstoneecho.echo_back.statistics.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.member.service.MemberService;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class StatsServiceTest {

    @Autowired private StatsService statsService;
    @Autowired private MemberService memberService;
    @Autowired private UserRepository userRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppProperties appProperties;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("attendance: completedAt 을 KST 일자로 변환해 attendance.days 에 누적 streak 매핑")
    void attendanceConvertsCompletedAtToKstDay() {
        User user = newUser("attkst");
        Script script = seedScript("att1");

        Instant t1 = LocalDate.of(2026, 5, 3).atStartOfDay(kst()).plusHours(10).toInstant();
        Instant t2 = LocalDate.of(2026, 5, 4).atStartOfDay(kst()).plusHours(9).toInstant();
        completeFeedbackAt(user, script, "First", t1);
        completeFeedbackAt(user, script, "Second", t2);

        StatsResponse response = statsService.getMyStats(user.getId(), 2026, 5);

        assertThat(response.attendance().year()).isEqualTo(2026);
        assertThat(response.attendance().month()).isEqualTo(5);
        assertThat(response.attendance().days()).containsEntry(3, 1);
        assertThat(response.attendance().days()).containsEntry(4, 2);
    }

    @Test
    @DisplayName("#13 attendance bucketing 은 KST 자정 경계에서 다음 일자로 분류된다")
    void attendanceBucketingBoundaryAtKstMidnight() {
        User user = newUser("attbound");
        Script script = seedScript("att2");

        Instant boundary = Instant.parse("2026-05-11T23:59:00Z"); // KST 2026-05-12 08:59
        completeFeedbackAt(user, script, "Boundary", boundary);

        StatsResponse response = statsService.getMyStats(user.getId(), 2026, 5);

        assertThat(response.attendance().days()).containsEntry(12, 1);
        assertThat(response.attendance().days()).doesNotContainKey(11);
    }

    @Test
    @DisplayName("#14 streak day 경계 (User.recordCompletion) 와 attendance 일자가 동일 ZoneId 로 일치")
    void streakAndAttendanceAreKstConsistent() {
        User user = newUser("streakkst");
        Script script = seedScript("att3");

        Instant boundary = Instant.parse("2026-05-11T23:59:00Z");
        completeFeedbackAt(user, script, "Boundary", boundary);
        transactionTemplate.executeWithoutResult(s -> {
            User u = userRepository.findById(user.getId()).orElseThrow();
            u.recordCompletion(boundary, 10, 7, kst());
        });

        StatsResponse response = statsService.getMyStats(user.getId(), 2026, 5);
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        Instant lastStudy = refreshed.getLastStudyAt();

        int streakDay = lastStudy.atZone(kst()).getDayOfMonth();
        assertThat(response.attendance().days()).containsKey(streakDay);
    }

    @Test
    @DisplayName("streak 은 7 캡을 넘지 않는다 (User.recordCompletion 정책)")
    void streakCapAt7() {
        User user = newUser("streakcap");

        for (int i = 0; i < 20; i++) {
            transactionTemplate.executeWithoutResult(s -> {
                User u = userRepository.findById(user.getId()).orElseThrow();
                Instant dayN = u.getLastStudyAt() == null
                        ? Instant.parse("2026-04-01T00:00:00Z")
                        : u.getLastStudyAt().atZone(kst()).plusDays(1).toInstant();
                u.recordCompletion(dayN, 10, 7, kst());
            });
        }

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getStreak()).isEqualTo(7);
        assertThat(refreshed.getExp()).isEqualTo(20 * 10);
    }

    @Test
    @DisplayName("year/month 검증 실패는 VALIDATION_FAILED")
    void invalidYearMonthThrowsValidationFailed() {
        User user = newUser("vfail");

        assertThatThrownBy(() -> statsService.getMyStats(user.getId(), 2026, 13))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode())
                                .isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> statsService.getMyStats(user.getId(), 1900, 5))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode())
                                .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("badges: 완료 피드백 0개 - FIRST_FEEDBACK / STREAK_7 둘 다 미달")
    void badgesReflectThresholds() {
        User user = newUser("badge0");
        YearMonth ym = currentYearMonth();

        StatsResponse response =
                statsService.getMyStats(user.getId(), ym.getYear(), ym.getMonthValue());

        assertThat(response.badges()).extracting(StatsResponse.Badge::id)
                .contains("FIRST_FEEDBACK", "STREAK_7");
        assertThat(badge(response, "FIRST_FEEDBACK").achieved()).isFalse();
        assertThat(badge(response, "STREAK_7").achieved()).isFalse();
    }

    @Test
    @DisplayName("USER_NOT_FOUND 는 존재하지 않는 사용자에 대해 던진다")
    void unknownUserThrowsUserNotFound() {
        assertThatThrownBy(() -> statsService.getMyStats(999_999_999L, 2026, 5))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // ---------- helpers ----------

    private ZoneId kst() {
        return ZoneId.of(appProperties.stats().zone());
    }

    private YearMonth currentYearMonth() {
        LocalDate today = LocalDate.now(kst());
        return YearMonth.of(today.getYear(), today.getMonthValue());
    }

    private static StatsResponse.Badge badge(StatsResponse response, String id) {
        return response.badges().stream()
                .filter(b -> id.equals(b.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("badge missing: " + id));
    }

    private Script seedScript(String suffix) {
        return transactionTemplate.execute(s -> {
            Track track = trackRepository.save(
                    Track.create("T-stats-" + suffix + "-" + System.nanoTime(), "d", 1));
            return scriptRepository.save(
                    Script.createChapter(track, 1, "S-" + suffix,
                            "content", Difficulty.EASY, null, null));
        });
    }

    private User newUser(String localPart) {
        return transactionTemplate.execute(s -> userRepository.save(User.signup(
                localPart + System.nanoTime(),
                localPart + "-" + System.nanoTime() + "@test.com",
                passwordEncoder.encode("Password1!"),
                "Nick")));
    }

    private void completeFeedbackAt(User user, Script script, String title, Instant completedAt) {
        Long fbId = transactionTemplate.execute(s ->
                feedbackRepository.save(
                        PronunciationFeedback.forScript(
                                user, script, title, 70.0, "TH", "think", "g")).getId());
        int affected = transactionTemplate.execute(s ->
                feedbackRepository.markCompletedAtomically(fbId, user.getId(), completedAt));
        assertThat(affected).isEqualTo(1);
    }
}

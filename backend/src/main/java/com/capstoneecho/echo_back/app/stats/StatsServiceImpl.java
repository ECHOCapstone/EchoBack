package com.capstoneecho.echo_back.app.stats;

import com.capstoneecho.echo_back.app.feedback.FeedbackRepository;
import com.capstoneecho.echo_back.app.feedback.PronunciationFeedback;
import com.capstoneecho.echo_back.app.member.MemberService;
import com.capstoneecho.echo_back.app.session.SessionService;
import com.capstoneecho.echo_back.app.stats.dto.StatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 통계 화면을 채울 응답을 한곳에서 조립한다.
//
//   attendance   : 사용자의 PronunciationFeedback 일자별 누적 → 같은 달 day → 그 시점 streak
//   weeklyErrors : 최근 7일 PronunciationFeedback 의 PhonemeError canonical 음소 빈도 top 5
//   badges       : BadgePolicy 가 단일 정책으로 평가 (출석/마스터/맞춤 학습 등)
@Service
@Transactional(readOnly = true)
class StatsServiceImpl implements StatsService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int WEEKLY_TOP_N = 5;

    private final MemberService memberService;
    private final FeedbackRepository feedbackRepository;
    private final SessionService sessionService;
    private final BadgePolicy badgePolicy;

    StatsServiceImpl(
            MemberService memberService,
            FeedbackRepository feedbackRepository,
            SessionService sessionService,
            BadgePolicy badgePolicy
    ) {
        this.memberService = memberService;
        this.feedbackRepository = feedbackRepository;
        this.sessionService = sessionService;
        this.badgePolicy = badgePolicy;
    }

    @Override
    public StatsResponse getMe(Long userId, Integer year, Integer month) {
        var user = memberService.getById(userId);
        var feedbacks = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
        var totalSessions = sessionService.countMine(userId);
        var today = LocalDate.now(ZONE);
        // year/month 가 명시되면 그 월의 출석을, 아니면 오늘이 속한 월의 출석을 집계한다.
        var calendarTarget = (year != null && month != null)
                ? LocalDate.of(year, month, 1)
                : today;

        return new StatsResponse(
                user.getStreak(),
                user.getExp(),
                buildAttendance(calendarTarget, feedbacks),
                buildWeeklyErrors(today, feedbacks),
                badgePolicy.evaluate(feedbacks, user.getStreak(), totalSessions)
        );
    }

    private StatsResponse.Attendance buildAttendance(LocalDate target, List<PronunciationFeedback> feedbacks) {
        var year = target.getYear();
        var month = target.getMonthValue();
        var days = new TreeMap<Integer, Integer>();
        var prevDay = -1;
        var streak = 0;
        for (var f : feedbacks.reversed()) {
            var date = f.getCreatedAt().atZone(ZONE).toLocalDate();
            if (date.getYear() != year || date.getMonthValue() != month) continue;
            var day = date.getDayOfMonth();
            if (prevDay == day) continue;
            streak = (prevDay >= 0 && day == prevDay + 1) ? streak + 1 : 1;
            days.put(day, streak);
            prevDay = day;
        }
        return new StatsResponse.Attendance(year, month, days);
    }

    private List<StatsResponse.PhonemeFrequency> buildWeeklyErrors(
            LocalDate today,
            List<PronunciationFeedback> feedbacks
    ) {
        var weekAgo = today.minusDays(7);
        Map<String, Integer> counter = new HashMap<>();
        for (var f : feedbacks) {
            var date = f.getCreatedAt().atZone(ZONE).toLocalDate();
            if (date.isBefore(weekAgo)) continue;
            for (var err : f.getErrors()) {
                var key = err.getCanonical() != null ? err.getCanonical() : err.getPerceived();
                if (key == null) continue;
                counter.merge(key, 1, Integer::sum);
            }
        }
        return counter.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(WEEKLY_TOP_N)
                .map(e -> new StatsResponse.PhonemeFrequency(e.getKey(), e.getValue()))
                .toList();
    }
}

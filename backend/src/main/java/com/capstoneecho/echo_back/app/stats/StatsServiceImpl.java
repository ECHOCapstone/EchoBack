package com.capstoneecho.echo_back.app.stats;

import com.capstoneecho.echo_back.app.feedback.FeedbackRepository;
import com.capstoneecho.echo_back.app.feedback.PronunciationFeedback;
import com.capstoneecho.echo_back.app.member.MemberService;
import com.capstoneecho.echo_back.app.stats.dto.StatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 통계 화면을 채울 응답을 한곳에서 조립한다.
//
//   attendance   : 사용자의 PronunciationFeedback 일자별 누적 → 같은 달 day → 그 시점 streak
//   weeklyErrors : 최근 7일 PronunciationFeedback 의 PhonemeError canonical 음소 빈도 top 5
//   badges       : 누적 정확도 평균 80 이상이면 R/L 마스터 획득 (단순 휴리스틱)
@Service
@Transactional(readOnly = true)
class StatsServiceImpl implements StatsService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int WEEKLY_TOP_N = 5;

    private final MemberService memberService;
    private final FeedbackRepository feedbackRepository;

    StatsServiceImpl(MemberService memberService, FeedbackRepository feedbackRepository) {
        this.memberService = memberService;
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    public StatsResponse getMe(Long userId, Integer year, Integer month) {
        var user = memberService.getById(userId);
        var feedbacks = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
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
                buildBadges(feedbacks)
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

    private List<StatsResponse.Badge> buildBadges(List<PronunciationFeedback> feedbacks) {
        var avg = feedbacks.stream().mapToDouble(PronunciationFeedback::getAccuracy).average().orElse(0.0);
        var ordered = new LinkedHashMap<String, Boolean>();
        ordered.put("rl_master", avg >= 80.0);
        ordered.put("vb_master", false);
        ordered.put("th_master", false);
        ordered.put("seven_day_streak", false);
        ordered.put("first_step", !feedbacks.isEmpty());
        ordered.put("perfect_unit", feedbacks.stream().anyMatch(f -> f.getAccuracy() >= 95.0));
        return ordered.entrySet().stream()
                .map(e -> new StatsResponse.Badge(e.getKey(), badgeName(e.getKey()), e.getValue()))
                .toList();
    }

    private String badgeName(String id) {
        return switch (id) {
            case "rl_master" -> "R/L 마스터";
            case "vb_master" -> "V/B 마스터";
            case "th_master" -> "TH 마스터";
            case "seven_day_streak" -> "7일 연속 출석";
            case "first_step" -> "첫 학습 완료";
            case "perfect_unit" -> "완벽한 한 판";
            default -> id;
        };
    }
}

package com.capstoneecho.echo_back.statistics.stats.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse.Attendance;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse.Badge;
import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse.PhonemeFrequency;
import com.capstoneecho.echo_back.statistics.stats.support.BadgePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatsService {

    static final int WEEKLY_TOP_N = 5;
    static final int WEEKLY_WINDOW_DAYS = 7;
    private static final int YEAR_MIN = 2000;
    private static final int YEAR_MAX = 2999;

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final BadgePolicy badgePolicy;
    private final AppProperties appProperties;

    public StatsService(
            UserRepository userRepository,
            FeedbackRepository feedbackRepository,
            BadgePolicy badgePolicy,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.badgePolicy = badgePolicy;
        this.appProperties = appProperties;
    }

    public StatsResponse getMyStats(Long userId, Integer year, Integer month) {
        ZoneId zone = resolveZone();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        YearMonth ym = resolveYearMonth(year, month, zone);
        Attendance attendance = buildAttendance(userId, ym, zone);
        List<PhonemeFrequency> weeklyErrors = buildWeeklyErrors(userId, zone);
        long completedCount = feedbackRepository.countByUser_IdAndCompletedTrue(userId);
        List<Badge> badges = badgePolicy.evaluate(user, completedCount);

        return new StatsResponse(
                user.getStreak(), user.getExp(), attendance, weeklyErrors, badges);
    }

    private ZoneId resolveZone() {
        String zone = appProperties.stats() == null ? null : appProperties.stats().zone();
        return (zone == null || zone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(zone);
    }

    private YearMonth resolveYearMonth(Integer year, Integer month, ZoneId zone) {
        if (year == null && month == null) {
            LocalDate today = LocalDate.now(zone);
            return YearMonth.of(today.getYear(), today.getMonthValue());
        }
        if (year == null || month == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (year < YEAR_MIN || year > YEAR_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return YearMonth.of(year, month);
    }

    private Attendance buildAttendance(Long userId, YearMonth ym, ZoneId zone) {
        Instant start = ym.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<Instant> instants = feedbackRepository.findCompletedAtInRange(userId, start, end);

        Map<Integer, Long> dayCounts = new TreeMap<>();
        for (Instant i : instants) {
            int day = i.atZone(zone).getDayOfMonth();
            dayCounts.merge(day, 1L, Long::sum);
        }

        Map<Integer, Integer> days = new LinkedHashMap<>();
        int cumulative = 0;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            if (dayCounts.containsKey(d)) {
                cumulative += 1;
                days.put(d, cumulative);
            } else {
                cumulative = 0;
            }
        }
        return new Attendance(ym.getYear(), ym.getMonthValue(), days);
    }

    private List<PhonemeFrequency> buildWeeklyErrors(Long userId, ZoneId zone) {
        Instant now = Instant.now();
        Instant start = now.atZone(zone).minusDays(WEEKLY_WINDOW_DAYS).toInstant();
        List<String> weakPhonemes = feedbackRepository.findWeakPhonemesInRange(userId, start, now);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String phoneme : weakPhonemes) {
            if (phoneme == null || phoneme.isBlank()) {
                continue;
            }
            counts.merge(phoneme, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(WEEKLY_TOP_N)
                .map(e -> new PhonemeFrequency(e.getKey(), e.getValue()))
                .toList();
    }
}

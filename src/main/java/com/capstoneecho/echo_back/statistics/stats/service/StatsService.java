package com.capstoneecho.echo_back.statistics.stats.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
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

    // 사용자가 입력하는 year 의 안전 범위. 명백한 오타 / 악의 입력만 차단한다.
    private static final int YEAR_MIN = 2000;
    private static final int YEAR_MAX = 2999;

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final BadgePolicy badgePolicy;
    private final StatsZoneProvider statsZoneProvider;
    private final int weeklyTopN;
    private final int weeklyWindowDays;

    public StatsService(
            UserRepository userRepository,
            FeedbackRepository feedbackRepository,
            BadgePolicy badgePolicy,
            StatsZoneProvider statsZoneProvider,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.badgePolicy = badgePolicy;
        this.statsZoneProvider = statsZoneProvider;
        AppProperties.Gamification g = appProperties.gamification();
        this.weeklyTopN = g.weeklyTopN();
        this.weeklyWindowDays = g.weeklyWindowDays();
    }

    public StatsResponse getMyStats(Long userId, Integer year, Integer month) {
        ZoneId zone = statsZoneProvider.zone();
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

    // 한 달치 일자 → 누적 연속 학습일 매핑. 학습 누락 일에는 누적이 초기화된다.
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

    // 최근 N 일 동안 가장 자주 약점으로 잡힌 음소 상위 K 개를 빈도순으로 돌려준다.
    private List<PhonemeFrequency> buildWeeklyErrors(Long userId, ZoneId zone) {
        Instant now = Instant.now();
        Instant start = now.atZone(zone).minusDays(weeklyWindowDays).toInstant();
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
                .limit(weeklyTopN)
                .map(e -> new PhonemeFrequency(e.getKey(), e.getValue()))
                .toList();
    }
}

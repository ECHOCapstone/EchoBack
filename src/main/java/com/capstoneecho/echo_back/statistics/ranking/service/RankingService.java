package com.capstoneecho.echo_back.statistics.ranking.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.statistics.ranking.dto.RankingResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 주간 발음 정확도 랭킹의 단일 진입점. 최근 N 일 동안 완료된 피드백을 사용자 단위로 집계해
// "평균 정확도 + 최소 활동 임계" 정책으로 정렬한 결과를 응답한다.
//
// 정책 요지:
//   1) 윈도 = [오늘 - (N-1)일, 오늘] (zone 기준 자정 경계).
//   2) 사용자별 평균 정확도 + 완료 횟수 집계.
//   3) 활동 횟수가 minActivityCount 미만이면 랭킹 후보에서 제외 (운에 의한 1등 방지).
//   4) 평균 정확도 내림차순 → 활동 횟수 내림차순으로 정렬 후 Top N 반환.
//   5) 본인이 Top N 밖이라도 자기 순위 / 진척 정보는 메타로 함께 내려 준다.
//
// 모든 정책 상수 (windowDays, topN, minActivityCount) 는 RuntimeSettings 로부터 읽어
// 어드민이 런타임에 조정할 수 있다.
@Service
@Transactional(readOnly = true)
public class RankingService {

    // 기간 표기 포맷 — "M/d" (예: "5/30 ~ 6/5"). FE 가 그대로 노출한다.
    private static final DateTimeFormatter PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("M/d", Locale.KOREAN);

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final StatsZoneProvider statsZoneProvider;
    private final RuntimeSettings settings;

    public RankingService(
            FeedbackRepository feedbackRepository,
            UserRepository userRepository,
            StatsZoneProvider statsZoneProvider,
            RuntimeSettings settings) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.statsZoneProvider = statsZoneProvider;
        this.settings = settings;
    }

    public RankingResponse weekly(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        int windowDays = Math.max(1, settings.weeklyWindowDays());
        int topN = Math.max(1, settings.weeklyTopN());
        int minActivity = Math.max(1, settings.rankingMinActivityCount());

        ZoneId zone = statsZoneProvider.zone();
        LocalDate endDate = LocalDate.now(zone);
        LocalDate startDate = endDate.minusDays(windowDays - 1L);
        Instant start = startDate.atStartOfDay(zone).toInstant();
        Instant endExclusive = endDate.plusDays(1).atStartOfDay(zone).toInstant();

        Map<Long, Aggregate> byUser = aggregateByUser(
                feedbackRepository.findCompletedInRange(start, endExclusive));

        List<Ranked> ranked = sortQualifiedCandidates(byUser, userId, minActivity);

        List<RankingResponse.Entry> entries = takeTop(ranked, topN);
        OwnStatus own = resolveOwn(ranked, byUser, userId, topN);

        return new RankingResponse(
                formatPeriod(startDate, endDate),
                windowDays,
                minActivity,
                ranked.size(),
                own.rank,
                own.accuracy,
                own.activityCount,
                own.shownInEntries,
                entries);
    }

    // 사용자별 (닉네임 + 정확도 합 + 완료 횟수) 누적. 같은 사용자의 여러 피드백은 평균에 합산된다.
    private Map<Long, Aggregate> aggregateByUser(List<PronunciationFeedback> feedbacks) {
        Map<Long, Aggregate> bucket = new HashMap<>();
        for (PronunciationFeedback f : feedbacks) {
            Long uid = f.getUser().getId();
            Aggregate agg = bucket.computeIfAbsent(uid, k -> new Aggregate(f.getUser().getNickname()));
            agg.add(f.getAccuracy());
        }
        return bucket;
    }

    // 임계를 통과한 후보만 골라 평균 정확도 내림차순 → 활동 횟수 내림차순으로 정렬한다.
    // 같은 평균이면 더 많이 학습한 쪽이 위로 — 꾸준함을 보상하는 tie-breaker.
    private List<Ranked> sortQualifiedCandidates(
            Map<Long, Aggregate> byUser, Long viewerId, int minActivity) {
        List<Ranked> ranked = new ArrayList<>();
        for (Map.Entry<Long, Aggregate> e : byUser.entrySet()) {
            Aggregate agg = e.getValue();
            if (agg.count < minActivity) {
                continue;
            }
            ranked.add(new Ranked(
                    e.getKey(), agg.nickname, agg.average(), agg.count,
                    e.getKey().equals(viewerId)));
        }
        // 두 단계 모두 내림차순. 첫 단계 결과에 .thenComparingInt(...).reversed() 를 붙이면
        // 후행 .reversed() 가 합쳐진 비교자 전체의 부호를 다시 뒤집어 의도와 반대(전부 오름차순)가 된다.
        // 단계별 Comparator 를 따로 만들고 thenComparing 으로 합치는 형태가 안전하다.
        Comparator<Ranked> byAccuracyDesc = Comparator.comparingDouble(Ranked::accuracy).reversed();
        Comparator<Ranked> byActivityDesc = Comparator.comparingInt(Ranked::activityCount).reversed();
        ranked.sort(byAccuracyDesc.thenComparing(byActivityDesc));
        return ranked;
    }

    // 정렬된 후보의 상위 topN 을 응답용 Entry 로 변환한다.
    private List<RankingResponse.Entry> takeTop(List<Ranked> ranked, int topN) {
        int limit = Math.min(topN, ranked.size());
        List<RankingResponse.Entry> entries = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Ranked r = ranked.get(i);
            entries.add(new RankingResponse.Entry(
                    i + 1, r.nickname, r.accuracy, r.activityCount, r.isMe));
        }
        return entries;
    }

    // 본인 메타를 계산한다. 임계 통과면 ranked 안의 위치로, 미통과여도 활동 기록은 그대로 노출한다.
    private OwnStatus resolveOwn(
            List<Ranked> ranked, Map<Long, Aggregate> byUser, Long userId, int topN) {
        for (int i = 0; i < ranked.size(); i++) {
            Ranked r = ranked.get(i);
            if (!r.isMe) continue;
            int rank = i + 1;
            return new OwnStatus(rank, r.accuracy, r.activityCount, rank <= topN);
        }
        Aggregate agg = byUser.get(userId);
        if (agg == null) {
            return new OwnStatus(0, 0.0, 0, false);
        }
        return new OwnStatus(0, agg.average(), agg.count, false);
    }

    private static String formatPeriod(LocalDate start, LocalDate end) {
        return PERIOD_FORMATTER.format(start) + " ~ " + PERIOD_FORMATTER.format(end);
    }

    // 사용자 한 명의 윈도 내 누적 — 정확도 합과 완료 건수를 모은다.
    private static final class Aggregate {
        private final String nickname;
        private double sum;
        private int count;

        private Aggregate(String nickname) {
            this.nickname = nickname;
        }

        private void add(double accuracy) {
            sum += accuracy;
            count++;
        }

        private double average() {
            return count == 0 ? 0.0 : sum / count;
        }
    }

    // 정렬 후 사용자 한 명을 가리키는 임시 행. 응답 Entry 직전 단계.
    private record Ranked(
            Long userId, String nickname, double accuracy, int activityCount, boolean isMe) {}

    // 응답의 본인 메타 묶음. 임계 미통과여도 자기 진척 (평균 / 활동 횟수) 은 그대로 내려준다.
    private record OwnStatus(int rank, double accuracy, int activityCount, boolean shownInEntries) {}
}

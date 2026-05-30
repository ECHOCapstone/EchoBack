package com.capstoneecho.echo_back.statistics.ranking.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.capstoneecho.echo_back.pronunciation.feedback.repository.FeedbackRepository;
import com.capstoneecho.echo_back.statistics.ranking.dto.RankingResponse;
import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
import com.capstoneecho.echo_back.statistics.ranking.repository.DemoRankingEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RankingService {

    private final FeedbackRepository feedbackRepository;
    private final DemoRankingEntryRepository demoRankingEntryRepository;
    private final UserRepository userRepository;
    private final StatsZoneProvider statsZoneProvider;
    private final RuntimeSettings settings;

    public RankingService(
            FeedbackRepository feedbackRepository,
            DemoRankingEntryRepository demoRankingEntryRepository,
            UserRepository userRepository,
            StatsZoneProvider statsZoneProvider,
            RuntimeSettings settings) {
        this.feedbackRepository = feedbackRepository;
        this.demoRankingEntryRepository = demoRankingEntryRepository;
        this.userRepository = userRepository;
        this.statsZoneProvider = statsZoneProvider;
        this.settings = settings;
    }

    public RankingResponse today(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        ZoneId zone = statsZoneProvider.zone();
        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        List<PronunciationFeedback> todays = feedbackRepository.findCompletedInRange(start, end);

        Map<Long, Ranked> bestByUser = new HashMap<>();
        String unitTitle = settings.defaultRankingUnitTitle();
        Instant latestForUnit = null;
        for (PronunciationFeedback f : todays) {
            Long fbUserId = f.getUser().getId();
            double acc = f.getAccuracy();
            Ranked existing = bestByUser.get(fbUserId);
            if (existing == null || acc > existing.accuracy()) {
                bestByUser.put(fbUserId, new Ranked(
                        f.getUser().getNickname(), acc, fbUserId.equals(userId)));
            }
            if (latestForUnit == null
                    || (f.getCompletedAt() != null && f.getCompletedAt().isAfter(latestForUnit))) {
                latestForUnit = f.getCompletedAt();
                String title = resolveUnitTitle(f);
                if (title != null && !title.isBlank()) {
                    unitTitle = title;
                }
            }
        }

        List<Ranked> rows = new ArrayList<>(bestByUser.values());
        for (DemoRankingEntry seed : demoRankingEntryRepository.findAll()) {
            rows.add(new Ranked(seed.getNickname(), seed.getAccuracy(), false));
        }

        rows.sort(Comparator.comparingDouble(Ranked::accuracy).reversed());

        List<RankingResponse.Entry> entries = new ArrayList<>(rows.size());
        int myRank = 0;
        double myAccuracy = 0.0;
        for (int i = 0; i < rows.size(); i++) {
            Ranked r = rows.get(i);
            int rank = i + 1;
            entries.add(new RankingResponse.Entry(rank, r.nickname(), r.accuracy(), r.isMe()));
            if (r.isMe()) {
                myRank = rank;
                myAccuracy = r.accuracy();
            }
        }

        return new RankingResponse(unitTitle, myRank, rows.size(), myAccuracy, List.copyOf(entries));
    }

    // 가장 최근 완료된 피드백의 스크립트 제목을 단위 제목으로 채택한다.
    // 스크립트가 없거나 빈 제목이면 피드백 자체 제목 → app.gamification 폴백 순.
    private String resolveUnitTitle(PronunciationFeedback f) {
        if (f.getScript() != null && f.getScript().getTitle() != null
                && !f.getScript().getTitle().isBlank()) {
            return f.getScript().getTitle();
        }
        if (f.getTitle() != null && !f.getTitle().isBlank()) {
            return f.getTitle();
        }
        return null;
    }

    // 랭킹 한 줄 — 사용자 최고 정확도 또는 데모 시드 한 건. isMe 로 요청자 본인을 표시한다.
    private record Ranked(String nickname, double accuracy, boolean isMe) {}
}

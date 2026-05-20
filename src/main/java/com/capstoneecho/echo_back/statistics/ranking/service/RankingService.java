package com.capstoneecho.echo_back.statistics.ranking.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
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

    private static final String DEFAULT_UNIT_TITLE = "오늘의 랭킹";

    private final FeedbackRepository feedbackRepository;
    private final DemoRankingEntryRepository demoRankingEntryRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public RankingService(
            FeedbackRepository feedbackRepository,
            DemoRankingEntryRepository demoRankingEntryRepository,
            UserRepository userRepository,
            AppProperties appProperties) {
        this.feedbackRepository = feedbackRepository;
        this.demoRankingEntryRepository = demoRankingEntryRepository;
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    public RankingResponse today(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        ZoneId zone = resolveZone();
        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        List<PronunciationFeedback> todays = feedbackRepository.findCompletedInRange(start, end);

        Map<Long, BestForUser> bestByUser = new HashMap<>();
        String unitTitle = DEFAULT_UNIT_TITLE;
        Instant latestForUnit = null;
        for (PronunciationFeedback f : todays) {
            Long fbUserId = f.getUser().getId();
            double acc = f.getAccuracy();
            BestForUser existing = bestByUser.get(fbUserId);
            if (existing == null || acc > existing.accuracy()) {
                bestByUser.put(fbUserId, new BestForUser(
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

        List<RankingRow> rows = new ArrayList<>();
        for (BestForUser b : bestByUser.values()) {
            rows.add(new RankingRow(b.nickname(), b.accuracy(), b.isMe()));
        }
        for (DemoRankingEntry seed : demoRankingEntryRepository.findAll()) {
            rows.add(new RankingRow(seed.getNickname(), (double) seed.getAccuracy(), false));
        }

        rows.sort(Comparator.comparingDouble(RankingRow::accuracy).reversed());

        List<RankingResponse.Entry> entries = new ArrayList<>(rows.size());
        int myRank = 0;
        double myAccuracy = 0.0;
        for (int i = 0; i < rows.size(); i++) {
            RankingRow r = rows.get(i);
            int rank = i + 1;
            entries.add(new RankingResponse.Entry(rank, r.nickname(), r.accuracy(), r.isMe()));
            if (r.isMe()) {
                myRank = rank;
                myAccuracy = r.accuracy();
            }
        }

        return new RankingResponse(unitTitle, myRank, rows.size(), myAccuracy, List.copyOf(entries));
    }

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

    private ZoneId resolveZone() {
        String zone = appProperties.stats() == null ? null : appProperties.stats().zone();
        return (zone == null || zone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(zone);
    }

    private record BestForUser(String nickname, double accuracy, boolean isMe) {}

    private record RankingRow(String nickname, double accuracy, boolean isMe) {}
}

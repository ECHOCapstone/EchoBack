package com.capstoneecho.echo_back.app.ranking;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.feedback.FeedbackRepository;
import com.capstoneecho.echo_back.app.feedback.PronunciationFeedback;
import com.capstoneecho.echo_back.app.member.MemberService;
import com.capstoneecho.echo_back.app.ranking.dto.RankingResponse;
import com.capstoneecho.echo_back.app.script.ScriptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 사용자가 막 끝낸 챕터에 대한 정확도 랭킹.
//
// 데이터 소스
//   - 실제 사용자 피드백: 동일 scriptId 의 PronunciationFeedback 중 사용자별 최고 정확도
//   - 시연용 가짜 사용자: demo_ranking_entries 테이블의 행. 운영 전환 시 비우면 자동 제거된다.
//
// 닉네임 모자이크는 본인 외 사용자에 대해서만 적용한다 (앞 3글자 + *).
@Service
@Transactional(readOnly = true)
class RankingServiceImpl implements RankingService {

    private final ScriptRepository scriptRepository;
    private final FeedbackRepository feedbackRepository;
    private final MemberService memberService;
    private final DemoRankingEntryRepository demoRankingRepository;

    RankingServiceImpl(
            ScriptRepository scriptRepository,
            FeedbackRepository feedbackRepository,
            MemberService memberService,
            DemoRankingEntryRepository demoRankingRepository
    ) {
        this.scriptRepository = scriptRepository;
        this.feedbackRepository = feedbackRepository;
        this.memberService = memberService;
        this.demoRankingRepository = demoRankingRepository;
    }

    @Override
    public RankingResponse today(Long userId) {
        // 사용자가 막 끝낸 챕터의 unitTitle 을 우선 사용한다. 가장 최근 PronunciationFeedback 의
        // scriptId 로 챕터를 찾고, 학습 이력이 전혀 없을 때만 첫 시드 챕터를 fallback 으로 쓴다.
        var feedbacksDesc = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
        var unit = feedbacksDesc.stream()
                .map(PronunciationFeedback::getScriptId)
                .filter(scriptId -> scriptId != null)
                .findFirst()
                .flatMap(scriptRepository::findById)
                .orElseGet(() -> scriptRepository.findByPresetTrueOrderByIdAsc().stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND)));
        var unitTitle = unit.getTitle();
        var me = memberService.getById(userId);

        var myAccuracy = feedbacksDesc.stream()
                .filter(f -> unit.getId().equals(f.getScriptId()))
                .mapToDouble(PronunciationFeedback::getAccuracy)
                .max()
                .orElse(0.0);

        var ranked = new ArrayList<RankingResponse.Entry>();
        for (var demo : demoRankingRepository.findAllByOrderByAccuracyDesc()) {
            ranked.add(new RankingResponse.Entry(0, maskNickname(demo.getNickname()), demo.getAccuracy(), false));
        }
        ranked.add(new RankingResponse.Entry(0, me.getNickname(), myAccuracy, true));
        ranked.sort(Comparator.comparingDouble(RankingResponse.Entry::accuracy).reversed());

        var withRank = new ArrayList<RankingResponse.Entry>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            var e = ranked.get(i);
            withRank.add(new RankingResponse.Entry(i + 1, e.nickname(), e.accuracy(), e.isMe()));
        }
        var myRank = findMyRank(withRank);
        return new RankingResponse(unitTitle, myRank, withRank.size(), myAccuracy, withRank);
    }

    private int findMyRank(List<RankingResponse.Entry> entries) {
        for (var e : entries) {
            if (e.isMe()) return e.rank();
        }
        return entries.size();
    }

    private String maskNickname(String name) {
        if (name == null) return "";
        if (name.length() <= 3) return name;
        return name.substring(0, 3) + "*".repeat(Math.max(2, name.length() - 3));
    }
}

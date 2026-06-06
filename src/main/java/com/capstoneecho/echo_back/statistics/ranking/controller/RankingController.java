package com.capstoneecho.echo_back.statistics.ranking.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.statistics.ranking.dto.RankingResponse;
import com.capstoneecho.echo_back.statistics.ranking.service.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 주간 랭킹 단일 진입점. 최근 N 일 평균 정확도 기준으로 정렬된 Top N 과 본인 메타를 응답한다.
// 윈도 일수 / Top 개수 / 임계 등 정책은 RankingService 가 RuntimeSettings 에서 읽는다.
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/weekly")
    public ApiResponse<RankingResponse> getWeekly(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(rankingService.weekly(principal.userId()));
    }
}

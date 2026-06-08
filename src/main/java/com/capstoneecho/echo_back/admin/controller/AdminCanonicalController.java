package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.service.CanonicalBackfillService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// canonical 일괄 갱신 엔드포인트. 현재는 챌린지만 backfill 대상이다 — learning_steps / session_sentences 는
// user-lock 기반이라 사용자가 시도하는 시점에 lazy 로 채워진다.
// SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/canonical")
public class AdminCanonicalController {

    private final CanonicalBackfillService backfillService;

    public AdminCanonicalController(CanonicalBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    // target=challenges 만 지원한다. 다른 값을 받으면 challenges 로 처리한다.
    @PostMapping("/backfill")
    public ApiResponse<CanonicalBackfillService.BackfillResult> backfill(
            @RequestParam(name = "target", defaultValue = "challenges") String target) {
        // target 인자는 향후 다른 도메인 확장 여지를 두려 받지만, 현재는 challenges 만 처리한다.
        return ApiResponse.success(backfillService.backfillChallenges());
    }
}

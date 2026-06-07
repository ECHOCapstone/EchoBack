package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.service.CanonicalBackfillService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// LLM canonical 일괄 갱신 엔드포인트. canonical_version < 2 인 학습 단계 / 챌린지 row 를 페이지 단위로
// LlmCanonicalGenerator 로 새로 만들어 캐시 컬럼에 저장한다. SecurityConfig 가 /api/admin/** 를
// ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/canonical")
public class AdminCanonicalController {

    private final CanonicalBackfillService backfillService;

    public AdminCanonicalController(CanonicalBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    // 한 페이지 backfill. 응답의 nextPage 가 null 이 될 때까지 반복 호출.
    //   target=steps      (기본) — learning_steps RECORD 단계
    //   target=challenges       — daily_challenges (전체, 단일 페이지로 응답)
    @PostMapping("/backfill")
    public ApiResponse<CanonicalBackfillService.BackfillResult> backfill(
            @RequestParam(name = "target", defaultValue = "steps") String target,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        CanonicalBackfillService.BackfillResult result = switch (target.toLowerCase()) {
            case "challenges" -> backfillService.backfillChallenges();
            default -> backfillService.backfillStepsPage(page, size);
        };
        return ApiResponse.success(result);
    }
}

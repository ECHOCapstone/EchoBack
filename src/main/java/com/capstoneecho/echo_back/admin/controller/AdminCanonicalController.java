package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.service.CanonicalBackfillService;
import com.capstoneecho.echo_back.admin.service.CanonicalBackfillService.BackfillResult;
import com.capstoneecho.echo_back.admin.service.CanonicalBackfillService.Target;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// canonical 일괄 갱신 엔드포인트. learning_steps / session_sentences / daily_challenges 세 도메인 모두
// canonical_cached_json 이 NULL 인 행을 LLM 호출로 채운다. 부팅 backfill (CanonicalBootstrapper) 토글이
// 꺼진 환경에서 어드민이 강제로 채워 넣는 경로.
// SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/canonical")
public class AdminCanonicalController {

    private final CanonicalBackfillService backfillService;

    public AdminCanonicalController(CanonicalBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    // target=steps | sentences | challenges | all. 미지정이면 all 로 세 도메인 모두 처리한다.
    // 알 수 없는 값은 INVALID_REQUEST 로 거절한다 — silent fallback 금지.
    @PostMapping("/backfill")
    public ApiResponse<List<BackfillResult>> backfill(
            @RequestParam(name = "target", defaultValue = "all") String target) {
        String normalized = target == null ? "all" : target.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("all")) {
            return ApiResponse.success(backfillService.backfillAll());
        }
        Target resolved = resolve(normalized);
        return ApiResponse.success(List.of(backfillService.backfill(resolved)));
    }

    private Target resolve(String value) {
        return switch (value) {
            case "steps", "learning_steps" -> Target.STEPS;
            case "sentences", "session_sentences" -> Target.SENTENCES;
            case "challenges", "daily_challenges" -> Target.CHALLENGES;
            default -> throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "지원하지 않는 backfill target: " + value);
        };
    }
}

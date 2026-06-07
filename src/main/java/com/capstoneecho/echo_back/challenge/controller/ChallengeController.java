package com.capstoneecho.echo_back.challenge.controller;

import com.capstoneecho.echo_back.challenge.dto.ChallengeAttemptResponse;
import com.capstoneecho.echo_back.challenge.dto.ChallengeHistoryItem;
import com.capstoneecho.echo_back.challenge.dto.ChallengeRankingResponse;
import com.capstoneecho.echo_back.challenge.dto.ChallengeResponse;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.service.ChallengeAttemptService;
import com.capstoneecho.echo_back.challenge.service.ChallengeHistoryService;
import com.capstoneecho.echo_back.challenge.service.ChallengeRankingService;
import com.capstoneecho.echo_back.challenge.service.ChallengeService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 사용자 시점의 "오늘의 챌린지" 진입점. 현재 챌린지 조회 / 도전 제출 / 랭킹 / 히스토리를 단일 경로로 묶는다.
//   GET  /api/challenges/current        — 현재 활성 챌린지 + 본인 메타 (도전 횟수 / best)
//   POST /api/challenges/current/attempts — 발음 도전 (멀티파트 audio)
//   GET  /api/challenges/{id}/ranking   — 챌린지의 사용자 best 랭킹
//   GET  /api/challenges/history        — 최신 N개 챌린지 + 본인 결과
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final ChallengeService challengeService;
    private final ChallengeAttemptService attemptService;
    private final ChallengeRankingService rankingService;
    private final ChallengeHistoryService historyService;

    public ChallengeController(
            ChallengeService challengeService,
            ChallengeAttemptService attemptService,
            ChallengeRankingService rankingService,
            ChallengeHistoryService historyService) {
        this.challengeService = challengeService;
        this.attemptService = attemptService;
        this.rankingService = rankingService;
        this.historyService = historyService;
    }

    @GetMapping("/current")
    public ApiResponse<ChallengeResponse> current(@CurrentUser JwtPrincipal principal) {
        Optional<DailyChallenge> active = challengeService.findActive();
        if (active.isEmpty()) {
            return ApiResponse.success(null);
        }
        DailyChallenge challenge = active.get();
        int todayCount = (int) attemptService.todayAttempts(principal.userId());
        Double myBest = attemptService.findUserBestScore(challenge.getId(), principal.userId());
        return ApiResponse.success(ChallengeResponse.of(
                challenge, todayCount, attemptService.dailyAttemptLimit(), myBest));
    }

    @PostMapping(value = "/current/attempts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChallengeAttemptResponse> attempt(
            @CurrentUser JwtPrincipal principal,
            @RequestParam("audio") MultipartFile audio) {
        byte[] bytes = readBytes(audio);
        return ApiResponse.success(attemptService.submit(principal.userId(), bytes));
    }

    @GetMapping("/{challengeId}/ranking")
    public ApiResponse<ChallengeRankingResponse> ranking(
            @org.springframework.web.bind.annotation.PathVariable Long challengeId,
            @CurrentUser JwtPrincipal principal) {
        DailyChallenge challenge = challengeService.requireById(challengeId);
        return ApiResponse.success(rankingService.forChallenge(challenge, principal.userId()));
    }

    @GetMapping("/current/ranking")
    public ApiResponse<ChallengeRankingResponse> currentRanking(@CurrentUser JwtPrincipal principal) {
        DailyChallenge challenge = challengeService.requireActive();
        return ApiResponse.success(rankingService.forChallenge(challenge, principal.userId()));
    }

    @GetMapping("/history")
    public ApiResponse<List<ChallengeHistoryItem>> history(
            @CurrentUser JwtPrincipal principal,
            @RequestParam(name = "limit", required = false, defaultValue = "" + DEFAULT_HISTORY_LIMIT) int limit) {
        return ApiResponse.success(historyService.recent(principal.userId(), limit));
    }

    // multipart 가 IOException 을 던지면 단순한 INVALID_REQUEST 가 아닌 업로드 단계 실패임을 명확히 한다.
    private static byte[] readBytes(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read uploaded audio", e);
        } catch (NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}

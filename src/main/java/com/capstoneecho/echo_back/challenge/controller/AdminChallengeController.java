package com.capstoneecho.echo_back.challenge.controller;

import com.capstoneecho.echo_back.challenge.dto.AdminChallengeListItem;
import com.capstoneecho.echo_back.challenge.dto.CreateChallengeRequest;
import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeAttemptRepository.UserBestProjection;
import com.capstoneecho.echo_back.challenge.service.ChallengeService;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 어드민 챌린지 관리 진입점. SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
// 활성 챌린지는 한 시점에 단 하나만 유지되도록 ChallengeService 가 도메인 차원에서 강제한다.
@RestController
@RequestMapping("/api/admin/challenges")
public class AdminChallengeController {

    private static final int DEFAULT_LIST_LIMIT = 50;

    private final ChallengeService challengeService;
    private final DailyChallengeAttemptRepository attemptRepository;

    public AdminChallengeController(
            ChallengeService challengeService,
            DailyChallengeAttemptRepository attemptRepository) {
        this.challengeService = challengeService;
        this.attemptRepository = attemptRepository;
    }

    @PostMapping
    public ApiResponse<AdminChallengeListItem> create(
            @Valid @RequestBody CreateChallengeRequest request) {
        DailyChallenge challenge = challengeService.create(
                request.targetText(), request.koreanTranslation(), request.activate());
        return ApiResponse.success(toListItem(challenge));
    }

    @PostMapping("/{challengeId}/activate")
    public ApiResponse<AdminChallengeListItem> activate(@PathVariable Long challengeId) {
        DailyChallenge challenge = challengeService.activate(challengeId);
        return ApiResponse.success(toListItem(challenge));
    }

    @DeleteMapping("/{challengeId}/activation")
    public ApiResponse<AdminChallengeListItem> deactivate(@PathVariable Long challengeId) {
        challengeService.deactivate(challengeId);
        DailyChallenge challenge = challengeService.requireById(challengeId);
        return ApiResponse.success(toListItem(challenge));
    }

    @GetMapping
    public ApiResponse<List<AdminChallengeListItem>> list(
            @RequestParam(name = "limit", required = false, defaultValue = "" + DEFAULT_LIST_LIMIT) int limit) {
        List<DailyChallenge> challenges = challengeService.findAllOrderedByCreatedDesc(limit);
        List<AdminChallengeListItem> items = new ArrayList<>(challenges.size());
        for (DailyChallenge c : challenges) {
            items.add(toListItem(c));
        }
        return ApiResponse.success(items);
    }

    // 어드민 응답 한 행을 만든다. totalAttempts 와 totalParticipants 는 랭킹 JPQL 결과 1번으로 함께 구한다.
    private AdminChallengeListItem toListItem(DailyChallenge challenge) {
        List<UserBestProjection> bests = attemptRepository.findUserBestsByChallengeId(challenge.getId());
        long totalParticipants = bests.size();
        long totalAttempts = 0L;
        for (UserBestProjection p : bests) {
            totalAttempts += p.getAttempts();
        }
        return AdminChallengeListItem.of(challenge, totalAttempts, totalParticipants);
    }
}

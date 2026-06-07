package com.capstoneecho.echo_back.challenge.dto;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.util.List;

// 사용자가 챌린지에 한 번 도전한 직후의 응답.
//   attemptId          : 시도 식별자 (FE 가 차후 추가 액션에 사용 가능).
//   score              : 채점 결과 0~100.
//   passThreshold      : 동일 정책 — FE 가 같은 게이지로 색칠.
//   isMyNewBest        : 이번 시도가 본인 best 를 갱신했는지 — 효과음 / 애니메이션 트리거에 사용.
//   attemptsToday / attemptsLimit : 도전 후 갱신된 카운트.
//   perceived / canonical / errors : 음소 시각화용. 학습자에게 약점 음소를 그대로 노출한다.
public record ChallengeAttemptResponse(
        Long attemptId,
        double score,
        double passThreshold,
        boolean isMyNewBest,
        int attemptsToday,
        int attemptsLimit,
        List<String> perceived,
        List<String> canonical,
        List<AnalyzeError> errors
) {}

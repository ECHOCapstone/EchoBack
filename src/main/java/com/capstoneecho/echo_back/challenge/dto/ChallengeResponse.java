package com.capstoneecho.echo_back.challenge.dto;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import java.time.Instant;

// 현재 활성 챌린지의 사용자 시점 응답. 메인 카드 / 챌린지 상세 페이지가 같은 객체를 본다.
//   id, targetText, koreanTranslation : 챌린지 본문.
//   activatedAt                       : 활성화 시점 — 랭킹 윈도 시작 안내용.
//   attemptsToday / attemptsLimit     : 사용자가 오늘 시도한 횟수 / 하루 상한. 상한 도달 시 도전 버튼 비활성.
//   myBestScore                       : 사용자의 현재 챌린지 best 점수 (null = 미도전).
public record ChallengeResponse(
        Long id,
        String targetText,
        String koreanTranslation,
        Instant activatedAt,
        int attemptsToday,
        int attemptsLimit,
        Double myBestScore
) {
    public static ChallengeResponse of(
            DailyChallenge challenge, int attemptsToday, int attemptsLimit, Double myBestScore) {
        return new ChallengeResponse(
                challenge.getId(),
                challenge.getTargetText(),
                challenge.getKoreanTranslation(),
                challenge.getActivatedAt(),
                attemptsToday,
                attemptsLimit,
                myBestScore);
    }
}

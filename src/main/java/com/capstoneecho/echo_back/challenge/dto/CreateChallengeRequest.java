package com.capstoneecho.echo_back.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 어드민이 새 챌린지를 등록할 때의 요청. activate=true 면 등록과 동시에 이전 활성 챌린지를 종료하고
// 본 챌린지를 활성으로 띄운다.
public record CreateChallengeRequest(
        @NotBlank @Size(max = 500) String targetText,
        @Size(max = 500) String koreanTranslation,
        boolean activate
) {}

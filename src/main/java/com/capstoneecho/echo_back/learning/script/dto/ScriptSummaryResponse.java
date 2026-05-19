package com.capstoneecho.echo_back.learning.script.dto;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;

// 학습 단위 목록 페이지에서 사용하는 요약 정보.
public record ScriptSummaryResponse(
        Long id,
        String title,
        Difficulty difficulty,
        boolean isPreset
) {

    public static ScriptSummaryResponse from(Script script) {
        return new ScriptSummaryResponse(
                script.getId(),
                script.getTitle(),
                script.getDifficulty(),
                script.isPreset()
        );
    }
}

package com.capstoneecho.echo_back.external.llm;

// LLM 이 돌려준 점수를 유효 범위 0~100 정수로 보정하는 공용 헬퍼.
final class LlmScores {

    private LlmScores() {
    }

    static int clampScore(int v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }
}

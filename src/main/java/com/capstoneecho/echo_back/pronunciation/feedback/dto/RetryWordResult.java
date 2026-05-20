package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import com.capstoneecho.echo_back.external.llm.PhonemeTip;
import java.util.List;

// 단어 / 구 재시도 응답.
// correct 는 LLM 의 정성 판정, passed 는 점수 임계 (app.gamification.pass-threshold) 기준.
// retryRecommended 는 클라이언트의 "재시도" 분기 SSOT.
public record RetryWordResult(
        boolean correct,
        boolean passed,
        boolean retryRecommended,
        List<String> perceived,
        List<String> canonical,
        double score,
        String guidanceKr,
        List<PhonemeTip> phonemeTips) {

    public RetryWordResult {
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        canonical = canonical == null ? List.of() : List.copyOf(canonical);
        phonemeTips = phonemeTips == null ? List.of() : List.copyOf(phonemeTips);
    }
}

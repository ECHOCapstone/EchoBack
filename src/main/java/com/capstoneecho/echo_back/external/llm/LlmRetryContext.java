package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import java.util.List;

// 단어/구 재시도 시 LLM 에 전달하는 입력.
// canonicalWords 는 단어 자체의 LLM canonical (단일 단어 / 짧은 구 단위).
// priorAttempts 는 같은 feedback 의 이전 재시도들 — LLM 이 같은 실수를 반복하는지 짚어준다.
public record LlmRetryContext(
        String word,
        List<String> perceived,
        List<String> canonical,
        List<CanonicalWord> canonicalWords,
        List<PriorAttempt> priorAttempts
) {
    public LlmRetryContext {
        word = word == null ? "" : word;
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        canonical = canonical == null ? List.of() : List.copyOf(canonical);
        canonicalWords = canonicalWords == null ? List.of() : List.copyOf(canonicalWords);
        priorAttempts = priorAttempts == null ? List.of() : List.copyOf(priorAttempts);
    }
}

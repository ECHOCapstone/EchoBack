package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.time.Instant;
import java.util.List;

// 같은 step / 챕터 안에서 사용자가 이전에 시도한 한 건의 분석 요약.
// LLM 호출 시 priorAttempts 로 묶여 들어가 누적 컨텍스트로 활용된다.
public record PriorAttempt(
        Instant attemptedAt,
        Double score,
        String perceived,
        List<AnalyzeError> errors
) {
    public PriorAttempt {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}

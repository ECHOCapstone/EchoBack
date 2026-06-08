package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import java.util.List;

// 단어/구 재시도 채점 요청 시 LLM 에 전달하는 입력.
//   - word          : 연습 항목 (단어 또는 짧은 구).
//   - canonicalWords: 채점에 사용할 단어별 ARPABET 시퀀스. 서비스 레이어가 LlmCanonicalGenerator 호출로
//                     매번 즉석 생성해 채워준다 (단어 단위는 lock 미적용).
//   - perceived     : 모델 서버가 돌려준 ARPABET 음소 시퀀스.
//   - priorAttempts : 같은 feedback 의 이전 재시도들.
public record LlmRetryContext(
        String word,
        List<CanonicalWord> canonicalWords,
        List<String> perceived,
        List<PriorAttempt> priorAttempts
) {
    public LlmRetryContext {
        word = word == null ? "" : word;
        canonicalWords = canonicalWords == null ? List.of() : List.copyOf(canonicalWords);
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        priorAttempts = priorAttempts == null ? List.of() : List.copyOf(priorAttempts);
    }
}

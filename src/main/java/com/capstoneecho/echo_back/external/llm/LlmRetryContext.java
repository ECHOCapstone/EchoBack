package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import java.util.List;

// 단어/구 재시도 채점 요청 시 LLM 에 전달하는 입력.
//   - word          : 연습 항목 (단어 또는 짧은 구).
//   - canonicalWords: 채점에 사용할 단어별 ARPABET 시퀀스. 단어 단위 재시도는 콘텐츠 캐시가 없으므로
//                     서비스 레이어가 LlmCanonicalGenerator 로 매번 즉석 생성해 채워준다.
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

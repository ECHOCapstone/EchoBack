package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import java.util.List;

// 한 step 채점 요청 시 LLM 에 전달하는 입력. 모든 리스트는 null 입력 시 빈 리스트로 정규화.
//   - chapterTitle  : 챕터 제목 (피드백 톤 결정에 참고).
//   - targetText    : 원문 영어 문장.
//   - canonicalWords: 채점에 사용할 단어별 ARPABET 시퀀스. 호출 전에 서비스 레이어가 콘텐츠 캐시
//                     (steps / sentences / challenges) 에서 로드하거나 cache miss 시
//                     LlmCanonicalGenerator 로 즉석 생성해 채워준다. 채점 LLM 은 이 값을 그대로 신뢰한다.
//   - perceived     : 모델 서버가 돌려준 ARPABET 음소 시퀀스 (강세/SIL/SP 제거된 상태).
//   - priorAttempts : 같은 (사용자, step) 의 이전 시도들 (오래된 순).
public record LlmStepContext(
        String chapterTitle,
        String targetText,
        List<CanonicalWord> canonicalWords,
        List<String> perceived,
        List<PriorAttempt> priorAttempts
) {
    public LlmStepContext {
        chapterTitle = chapterTitle == null ? "" : chapterTitle;
        targetText = targetText == null ? "" : targetText;
        canonicalWords = canonicalWords == null ? List.of() : List.copyOf(canonicalWords);
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        priorAttempts = priorAttempts == null ? List.of() : List.copyOf(priorAttempts);
    }
}

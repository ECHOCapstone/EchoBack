package com.capstoneecho.echo_back.external.llm.canonical;

import java.util.List;

// 영어 텍스트의 자연스러운 General American 발음을 단어별 ARPABET 시퀀스로 만든다.
//   - text       : 정답 텍스트.
//   - perceived  : 학습자가 실제로 낸 음소 시퀀스 (선택). 있으면 자연 연결 발음을 반영한 canonical,
//                  없으면 표준 사전적 canonical 을 만든다. 빈 리스트 / null 모두 "없음" 으로 처리한다.
// 실패 시 BusinessException(CANONICAL_GENERATION_FAILED) 을 던진다 (silent fallback 금지).
public interface LlmCanonicalGenerator {

    CanonicalResult generate(String text, List<String> perceived);
}

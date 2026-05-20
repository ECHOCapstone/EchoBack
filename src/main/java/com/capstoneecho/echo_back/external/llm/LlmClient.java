package com.capstoneecho.echo_back.external.llm;

// LLM 호출 어댑터. 모든 메서드는 구조화된 record 를 돌려주며,
// 구현 실패 / 빈 응답 시 호출 측이 즉시 사용할 수 있도록 폴백 값을 채워서 반환한다 (null 금지).
public interface LlmClient {

    // 한 번의 녹음 (step) 분석을 받아 점수 / 가이드 / 약점 / 재학습 권고를 함께 돌려준다.
    LlmStepFeedback stepFeedback(LlmStepContext context);

    // 단어 또는 구의 재시도 분석을 받아 정답 여부 / 점수 / 단서를 돌려준다.
    LlmRetryFeedback retryFeedback(LlmRetryContext context);

    // 챕터 전체 흐름을 종합해 총평 / 강점 / 약점 / 다음 학습 항목을 돌려준다.
    LlmComprehensiveFeedback comprehensiveFeedback(LlmComprehensiveContext context);
}

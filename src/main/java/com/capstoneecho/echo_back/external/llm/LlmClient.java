package com.capstoneecho.echo_back.external.llm;

// LLM 호출 어댑터. 모든 한국어 가이던스는 폴백 포함 항상 non-empty 를 보장하고,
// wrongWords 는 약점 단어가 없으면 빈 리스트를 돌려준다.
public interface LlmClient {

    // 녹음 단위 요약 + 약점 단어 목록.
    RecordingGuidance summarizeRecording(LlmContext context);

    // 학습 단위 종합 피드백 한국어 문장.
    String summarizeFeedback(LlmContext context);

    // 단어 재시도 가이던스 한국어 문장.
    String retryGuidance(LlmContext context);

    // 약점 음소 등에 기반한 연습 단어 추천.
    String suggestPracticeWord(LlmContext context);
}

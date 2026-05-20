package com.capstoneecho.echo_back.external.llm;

/**
 * LLM 호출 어댑터 인터페이스 (IMPLEMENTATION_PLAN §8.2).
 *
 * <p>구현은 두 종류:
 * <ul>
 *   <li>{@link RuleBasedLlmFeedbackGenerator}: LLM 호출 없는 룰 기반 폴백 (기본).</li>
 *   <li>{@code GeminiLlmFeedbackGenerator}: Gemini API 호출 (추후).</li>
 * </ul>
 *
 * <p>계약:
 * <ul>
 *   <li>모든 메서드의 한국어 가이던스(`guidanceKr`) 는 폴백으로 항상 non-empty 보장.</li>
 *   <li>{@link RecordingGuidance#wrongWords()} 는 약점 단어 없으면 빈 리스트.</li>
 * </ul>
 */
public interface LlmClient {

    /**
     * RecordingService.upload 단계의 녹음 단위 요약.
     */
    RecordingGuidance summarizeRecording(LlmContext context);

    /**
     * FeedbackService.generate 단계의 학습 단위 종합 피드백 한국어 문장.
     */
    String summarizeFeedback(LlmContext context);

    /**
     * FeedbackService.retryWord 단계의 단어 재시도 가이던스.
     */
    String retryGuidance(LlmContext context);

    /**
     * PracticeWordResolver 폴백 — 약점 음소 등에 기반한 연습 단어 추천.
     */
    String suggestPracticeWord(LlmContext context);
}

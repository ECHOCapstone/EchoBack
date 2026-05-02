package com.capstoneecho.echo_back.app.llm;

/**
 * LLM 응답의 도메인 무관 표현.
 *
 * <p>LLM 제공자(REST, SDK 등)가 반환한 원문 컨텐츠만 담는 DTO이며, 어떠한 도메인 해석도 수행하지 않는다.
 */
public record LlmResponse(String content) {
}

package com.capstoneecho.echo_back.app.llm;

/**
 * 도메인 무관 LLM 호출 포트.
 *
 * <p>프롬프트를 전달해 생성된 컨텐츠를 {@link LlmResponse} DTO로 받는 역할만 수행한다.
 * 피드백/학습/통계 등 도메인 특화 프롬프트 빌드와 응답 해석은 각 도메인 계층에서 수행한다.
 */
public interface LlmClient {

    /**
     * 주어진 프롬프트를 LLM에 전송하고 생성된 컨텐츠를 반환한다.
     *
     * @param prompt LLM에 전달할 프롬프트 텍스트
     * @return LLM이 생성한 컨텐츠를 담은 응답 DTO
     */
    LlmResponse generate(String prompt);
}
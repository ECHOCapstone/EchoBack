package com.capstoneecho.echo_back.app.llm;

// 프롬프트를 보내 텍스트 응답을 받는 LLM 포트. 어떤 모델/공급자를 쓰는지는 구현체가 정한다.
public interface LlmClient {

    LlmResponse generate(String prompt);
}

package com.capstoneecho.echo_back.app.llm;

// LLM 이 돌려준 텍스트만 들고 있는 DTO. 응답이 비어 있을 수 있어 호출자가 그 케이스를 처리해야 한다.
public record LlmResponse(String content) {
}

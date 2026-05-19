package com.capstoneecho.echo_back.external.llm;

import tools.jackson.databind.JsonNode;

import java.util.Map;

// 프롬프트를 보내 텍스트 응답을 받는 LLM 포트. 어떤 모델/공급자를 쓰는지는 구현체가 정한다.
//   generate     : 자유 텍스트 응답
//   generateJson : 응답을 schema 에 맞춘 JSON 으로 강제. 호출자가 키별로 꺼내 쓴다.
public interface LlmClient {

    LlmResponse generate(String prompt);

    JsonNode generateJson(String prompt, Map<String, Object> schema);
}

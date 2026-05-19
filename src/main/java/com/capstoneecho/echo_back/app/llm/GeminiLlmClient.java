package com.capstoneecho.echo_back.app.llm;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Gemini generateContent 엔드포인트로 프롬프트를 보내고 첫 candidate 의 텍스트를 그대로 돌려준다.
// 빈으로 자동 등록되지 않고 LlmClientConfig 가 provider=gemini 일 때만 만들어 준다.
//
// generateJson 은 generationConfig.responseMimeType=application/json + responseSchema 를 같이 보내
// 모델이 JSON 으로만 응답하도록 강제하고, 그 텍스트를 JsonNode 로 파싱한다. 호출자는 키별로 꺼낸다.
//
// baseUrl/model 은 application.yaml 의 app.llm 에서 주입되어 운영 중 교체에 코드 변경이 필요 없다.
// docs: https://ai.google.dev/api/generate-content
public class GeminiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiLlmClient(String baseUrl, String apiKey, String model, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public LlmResponse generate(String prompt) {
        var response = call(prompt, null);
        return new LlmResponse(extractText(response));
    }

    @Override
    public JsonNode generateJson(String prompt, Map<String, Object> schema) {
        var generationConfig = new HashMap<String, Object>();
        generationConfig.put("responseMimeType", "application/json");
        if (schema != null) generationConfig.put("responseSchema", schema);
        var response = call(prompt, generationConfig);
        var text = extractText(response);
        if (text.isBlank()) return objectMapper.createObjectNode();
        return objectMapper.readTree(text);
    }

    private GeminiResponse call(String prompt, Map<String, Object> generationConfig) {
        var request = new HashMap<String, Object>();
        request.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        if (generationConfig != null) request.put("generationConfig", generationConfig);
        return restClient.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);
    }

    // 응답 어디든 비어 있을 수 있다. 빈 응답을 하나의 케이스로 묶어 빈 문자열을 돌려준다.
    private static String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return "";
        }
        var content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            return "";
        }
        var text = content.parts().get(0).text();
        return text != null ? text : "";
    }

    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
}

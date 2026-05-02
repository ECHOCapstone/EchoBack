package com.capstoneecho.echo_back.app.llm;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// Gemini generateContent 엔드포인트로 프롬프트를 보내고 첫 candidate 의 텍스트를 그대로 돌려준다.
// 빈으로 자동 등록되지 않고 LlmClientConfig 가 provider=gemini 일 때만 만들어 준다.
// 응답 후보가 비어 있으면 빈 텍스트로 떨어져 호출자가 fallback 으로 처리한다.
//
// docs: https://ai.google.dev/api/generate-content
public class GeminiLlmClient implements LlmClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final RestClient restClient;
    private final String model;

    public GeminiLlmClient(String apiKey, String model) {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.model = model;
    }

    @Override
    public LlmResponse generate(String prompt) {
        Map<String, Object> request = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        var response = restClient.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        return new LlmResponse(extractText(response));
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

package com.capstoneecho.echo_back.app.llm;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Gemini REST API 기반 {@link LlmClient} 구현.
 *
 * <p>Gemini `generateContent` 엔드포인트에 프롬프트를 보내고, 첫 번째 candidate의 텍스트를 그대로 반환한다.
 * 도메인 특화 프롬프트 생성 및 응답 파싱은 호출자(도메인 계층)의 책임이다.
 *
 * <p>이 클래스는 Spring 컴포넌트로 자동 등록되지 않는다. {@link LlmClientConfig} 의 {@code @Bean}
 * 메서드에서 어떤 프로바이더와 모델을 쓸지 명시적으로 선택한다.
 *
 * <p>참고: <a href="https://ai.google.dev/api/generate-content">Gemini generateContent API</a>
 */
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

        GeminiResponse response = restClient.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        String content = response.candidates().getFirst().content().parts().getFirst().text();
        return new LlmResponse(content);
    }

    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
}

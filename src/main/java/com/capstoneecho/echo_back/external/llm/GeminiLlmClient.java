package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

// Gemini REST API 의 generateContent + 구조화 출력 (responseFormat.text.schema) 을 사용해
// 세 종류의 피드백을 JSON 으로 받아 record 로 역직렬화한다.
// 시스템 프롬프트 (아학편 가이드 포함) 는 PromptCatalog 의 system.md 에서 로드해
// systemInstruction 필드로 매 요청에 주입한다.
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "gemini")
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    // 결정적이고 짧은 출력을 위해 낮은 temperature 와 충분한 outputTokens 를 고정한다.
    private static final double TEMPERATURE = 0.4;
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PromptCatalog prompts;
    private final RuleBasedLlmFallback fallback;
    private final String apiKey;
    private final String model;

    public GeminiLlmClient(
            AppProperties appProperties,
            ObjectMapper objectMapper,
            PromptCatalog prompts,
            RuleBasedLlmFallback fallback) {
        AppProperties.Llm llm = appProperties.llm();
        if (llm == null || llm.gemini() == null) {
            throw new IllegalStateException("app.llm.gemini configuration is required");
        }
        AppProperties.Llm.Gemini g = llm.gemini();
        if (g.apiKey() == null || g.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "app.llm.gemini.api-key must be configured (set GEMINI_API_KEY env)");
        }
        if (g.model() == null || g.model().isBlank()) {
            throw new IllegalStateException("app.llm.gemini.model must be configured");
        }
        if (g.baseUrl() == null || g.baseUrl().isBlank()) {
            throw new IllegalStateException("app.llm.gemini.base-url must be configured");
        }
        this.apiKey = g.apiKey();
        this.model = g.model();
        this.objectMapper = objectMapper;
        this.prompts = prompts;
        this.fallback = fallback;

        Duration timeout = Duration.ofMillis(g.timeoutMs() <= 0 ? 10_000 : g.timeoutMs());
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(g.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        String userPrompt = prompts.render("step-feedback", Map.of(
                "chapterTitle", context.chapterTitle(),
                "targetText", context.targetText(),
                "perceived", LlmContextSerializer.list(context.perceived()),
                "canonical", LlmContextSerializer.list(context.canonical()),
                "errors", LlmContextSerializer.errors(context.errors()),
                "weakPhoneme", nullSafe(context.weakPhoneme()),
                "currentScore", LlmContextSerializer.number(context.currentScore()),
                "priorAttempts", LlmContextSerializer.priorAttempts(context.priorAttempts())));
        LlmStepFeedback parsed = call(userPrompt, LlmJsonSchemas.stepFeedback(), LlmStepFeedback.class);
        return parsed != null ? parsed : fallback.stepFeedback(context);
    }

    @Override
    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        String userPrompt = prompts.render("retry-feedback", Map.of(
                "word", context.word(),
                "perceived", LlmContextSerializer.list(context.perceived()),
                "canonical", LlmContextSerializer.list(context.canonical()),
                "errors", LlmContextSerializer.errors(context.errors()),
                "weakPhoneme", nullSafe(context.weakPhoneme()),
                "currentScore", LlmContextSerializer.number(context.currentScore()),
                "priorAttempts", LlmContextSerializer.priorAttempts(context.priorAttempts())));
        LlmRetryFeedback parsed = call(userPrompt, LlmJsonSchemas.retryFeedback(), LlmRetryFeedback.class);
        return parsed != null ? parsed : fallback.retryFeedback(context);
    }

    @Override
    public LlmComprehensiveFeedback comprehensiveFeedback(LlmComprehensiveContext context) {
        String userPrompt = prompts.render("comprehensive-feedback", Map.of(
                "chapterTitle", context.chapterTitle(),
                "chapterContent", context.chapterContent(),
                "overallAccuracy", LlmContextSerializer.number(context.overallAccuracy()),
                "dominantWeakPhoneme", nullSafe(context.dominantWeakPhoneme()),
                "stepSummaries", LlmContextSerializer.stepSummaries(context.stepSummaries()),
                "aggregatedErrors", LlmContextSerializer.errors(context.aggregatedErrors())));
        LlmComprehensiveFeedback parsed =
                call(userPrompt, LlmJsonSchemas.comprehensiveFeedback(), LlmComprehensiveFeedback.class);
        return parsed != null ? parsed : fallback.comprehensiveFeedback(context);
    }

    // Gemini generateContent 호출. 실패 / 빈 응답 / JSON 파싱 실패 시 null 을 돌려 호출 측이 폴백을 사용한다.
    private <T> T call(String userPrompt, Map<String, Object> schema, Class<T> type) {
        Map<String, Object> body = buildBody(userPrompt, schema);
        try {
            GeminiResponse response = restClient.post()
                    .uri(uri -> uri
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            String json = extractText(response);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (RuntimeException ex) {
            log.warn("Gemini call failed: {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Gemini JSON parse failed: {}", ex.getMessage());
            return null;
        }
    }

    // generationConfig.responseFormat.text 로 JSON 강제, systemInstruction 으로 아학편 가이드 주입.
    private Map<String, Object> buildBody(String userPrompt, Map<String, Object> schema) {
        Map<String, Object> userPart = Map.of("text", userPrompt);
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(userPart));

        Map<String, Object> systemContent = Map.of(
                "parts", List.of(Map.of("text", prompts.raw("system"))));

        Map<String, Object> responseFormat = Map.of(
                "text", Map.of(
                        "mimeType", "application/json",
                        "schema", schema));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        generationConfig.put("responseFormat", responseFormat);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemContent);
        body.put("contents", List.of(userContent));
        body.put("generationConfig", generationConfig);
        return body;
    }

    // 응답 구조: candidates[0].content.parts[*].text 들을 이어 붙여 JSON 문자열로 돌려준다.
    private static String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        GeminiResponse.Candidate first = response.candidates().get(0);
        if (first == null || first.content() == null || first.content().parts() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (GeminiResponse.Part part : first.content().parts()) {
            if (part != null && part.text() != null) {
                sb.append(part.text());
            }
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "(미확정)" : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Candidate(Content content) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Content(List<Part> parts) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Part(String text) {}
    }
}

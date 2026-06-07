package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

// Gemini REST API 의 generateContent + 구조화 출력 (responseFormat.text.schema) 을 사용해
// 세 종류의 피드백을 JSON 으로 받아 record 로 역직렬화한다.
// 시스템 프롬프트 (아학편 가이드 포함) 는 PromptCatalog 의 system.md 에서 로드해
// systemInstruction 필드로 매 요청에 주입한다.
// 항상 등록되며, 실제 호출 여부는 DispatchingLlmClient 가 활성 provider 와 isAvailable() 로 결정한다.
@Component
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    // 결정적이고 짧은 출력을 위해 낮은 temperature 와 충분한 outputTokens 를 고정한다.
    private static final double TEMPERATURE = 0.4;
    private static final int MAX_OUTPUT_TOKENS = 1024;

    // yaml(app.llm.gemini.base-url) 누락 시에만 쓰는 최후 폴백. 모델 기본값은 별도 literal 을 두지 않고
    // 허용 모델 목록(app.llm.gemini.models)의 첫 항목에서 도출해 yaml 을 단일 출처로 유지한다.
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PromptCatalog prompts;
    private final RuleBasedLlmFallback fallback;
    private final SettingsService settings;
    private final String apiKey;
    private final String defaultModel;
    private final boolean available;

    public GeminiLlmClient(
            AppProperties appProperties,
            ObjectMapper objectMapper,
            PromptCatalog prompts,
            RuleBasedLlmFallback fallback,
            SettingsService settings) {
        this.objectMapper = objectMapper;
        this.prompts = prompts;
        this.fallback = fallback;
        this.settings = settings;

        AppProperties.Llm llm = appProperties.llm();
        AppProperties.Llm.Gemini g = llm == null ? null : llm.gemini();
        String key = g == null ? null : g.apiKey();
        String configuredModel = g == null ? null : g.model();
        String baseUrl = g == null ? null : g.baseUrl();
        long timeoutMs = g == null ? 0L : g.timeoutMs();

        this.apiKey = key == null ? "" : key;
        this.defaultModel = (configuredModel == null || configuredModel.isBlank())
                ? firstAllowedModel(g) : configuredModel;
        // apiKey 가 있어야 호출 가능하다. 없으면 DispatchingLlmClient 가 규칙 기반으로 보낸다.
        this.available = !this.apiKey.isBlank();

        Duration timeout = Duration.ofMillis(timeoutMs <= 0 ? 10_000 : timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl((baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl)
                .requestFactory(factory)
                .build();
    }

    // 모델 기본값을 허용 모델 목록(app.llm.gemini.models)의 첫 항목에서 도출한다.
    // yaml 의 models 목록을 단일 출처로 삼아, 코드에 별도 모델 literal 을 두지 않는다.
    private static String firstAllowedModel(AppProperties.Llm.Gemini gemini) {
        return gemini == null ? "" : gemini.safeModels().stream().findFirst().orElse("");
    }

    // apiKey 가 설정돼 호출 가능한 상태인지. DispatchingLlmClient 가 라우팅 판단에 쓴다.
    public boolean isAvailable() {
        return available;
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
        // 호출 시점의 활성 모델을 설정에서 읽는다 (어드민이 런타임에 바꿀 수 있다).
        String model = settings.getOrDefault(LlmSettingKeys.GEMINI_MODEL, defaultModel);
        try {
            GeminiResponse response = restClient.post()
                    .uri(uri -> uri
                            .path("/v1beta/models/{model}:generateContent")
                            .build(model))
                    // API 키는 헤더 (x-goog-api-key) 로 전달한다. 쿼리 파라미터로 보내면 액세스 로그 /
                    // 에러 응답 본문 / 외부 프록시 로그에 평문 노출될 수 있어 표준 안전 흐름과 어긋난다.
                    .header("x-goog-api-key", apiKey)
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
            // HTTP 호출 실패와 JSON 파싱 실패 모두 unchecked 라 한곳에서 잡는다.
            // 호출 측은 null 을 받아 규칙 기반 폴백으로 떨어진다. 원인 추적을 위해 throwable 을 함께 남긴다.
            log.warn("Gemini 호출/파싱 실패 — 규칙 기반 폴백 사용", ex);
            return null;
        }
    }

    // v1beta API 의 구식 JSON 강제 필드 (responseMimeType + responseSchema).
    // gemini.md 의 신식 responseFormat.text.{mimeType, schema} 는 SDK 자동 변환 / 미출시 endpoint 용 형식이라
    // 현재 v1beta REST 가 mime_type 의 enum 값으로 "application/json" 문자열을 reject 한다.
    // 구식 필드는 Gemini 1.5 / 2.0 / 2.5 / 3.x 까지 모든 모델에서 동작한다.
    // systemInstruction 으로 아학편 가이드를 주입한다.
    private Map<String, Object> buildBody(String userPrompt, Map<String, Object> schema) {
        Map<String, Object> userPart = Map.of("text", userPrompt);
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(userPart));

        Map<String, Object> systemContent = Map.of(
                "parts", List.of(Map.of("text", prompts.raw("system"))));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", schema);

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

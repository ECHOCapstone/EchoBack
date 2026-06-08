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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Gemini generateContent + 구조화 출력의 공통 HTTP/파싱 경로. 호출 측은 systemInstruction 본문,
// userPrompt, JSON schema, 결과 타입만 넘기면 된다.
//
// 실패 정책은 호출자가 선택한다: callOrNull() 은 실패 시 null 을 돌려 호출 측 폴백을 허용하고,
// callRequired() 는 실패 시 GeminiCallException 을 던져 호출 측에서 사용자 에러로 변환한다.
@Component
public class GeminiCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(GeminiCallExecutor.class);
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SettingsService settings;
    private final String apiKey;
    private final String defaultModel;
    // 어드민이 고를 수 있는 허용 모델 집합 (app.llm.gemini.models). 런타임 설정값 검증에 쓴다.
    private final java.util.Set<String> allowedModels;
    private final boolean available;

    public GeminiCallExecutor(
            AppProperties appProperties,
            ObjectMapper objectMapper,
            SettingsService settings) {
        this.objectMapper = objectMapper;
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
        this.allowedModels = g == null ? java.util.Set.of() : java.util.Set.copyOf(g.safeModels());
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

    public boolean isAvailable() {
        return available;
    }

    // 활성 모델 id (어드민 런타임 설정 우선, 없으면 yaml 기본값).
    // 런타임 설정이 허용 목록(app.llm.gemini.models)에 없으면(오타·제거·중단된 모델 등) 기본값으로
    // 폴백한다. 잘못된 모델 ID 하나로 모든 Gemini 호출이 404 로 죽는 것을 막는다.
    public String activeModel() {
        String configured = settings.getOrDefault(LlmSettingKeys.GEMINI_MODEL, defaultModel);
        if (allowedModels.isEmpty() || allowedModels.contains(configured)) {
            return configured;
        }
        log.warn("설정된 Gemini 모델 '{}' 이(가) 허용 목록 {} 에 없어 기본값 '{}' 로 폴백합니다.",
                configured, allowedModels, defaultModel);
        return defaultModel;
    }

    // 실패 시 null. step / retry / comprehensive feedback 처럼 규칙 기반 폴백이 허용되는 호출.
    public <T> T callOrNull(
            String systemInstruction,
            String userPrompt,
            Map<String, Object> schema,
            Class<T> type) {
        try {
            return callInternal(systemInstruction, userPrompt, schema, type);
        } catch (GeminiCallException ex) {
            return null;
        }
    }

    // 실패 시 GeminiCallException. canonical 생성처럼 사용자에게 명시적 에러를 노출해야 하는 호출.
    public <T> T callRequired(
            String systemInstruction,
            String userPrompt,
            Map<String, Object> schema,
            Class<T> type) {
        return callInternal(systemInstruction, userPrompt, schema, type);
    }

    private <T> T callInternal(
            String systemInstruction,
            String userPrompt,
            Map<String, Object> schema,
            Class<T> type) {
        Map<String, Object> body = buildBody(systemInstruction, userPrompt, schema);
        String model = activeModel();
        try {
            GeminiResponse response = restClient.post()
                    .uri(uri -> uri.path("/v1beta/models/{model}:generateContent").build(model))
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            String json = extractText(response);
            if (json == null || json.isBlank()) {
                throw new GeminiCallException("empty Gemini response", null);
            }
            return objectMapper.readValue(json, type);
        } catch (RestClientResponseException http) {
            HttpStatus status = HttpStatus.resolve(http.getStatusCode().value());
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                log.warn("Gemini 인증 실패 ({}) — API 키 확인 필요", status);
            } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Gemini 쿼터 초과 (429)");
            } else {
                log.warn("Gemini HTTP 오류 ({}) body={}", status, http.getResponseBodyAsString());
            }
            throw new GeminiCallException("Gemini HTTP " + status, http);
        } catch (ResourceAccessException network) {
            log.warn("Gemini 네트워크 오류: {}", network.getMessage());
            throw new GeminiCallException("Gemini 네트워크 오류", network);
        } catch (JacksonException parse) {
            log.error("Gemini 구조화 출력 파싱 실패 — 프롬프트/스키마 점검 필요", parse);
            throw new GeminiCallException("Gemini 구조화 출력 파싱 실패", parse);
        } catch (RuntimeException ex) {
            log.error("Gemini 호출 실패 (예측 못한 예외)", ex);
            throw new GeminiCallException("Gemini 호출 실패", ex);
        }
    }

    private static Map<String, Object> buildBody(
            String systemInstruction, String userPrompt, Map<String, Object> schema) {
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt)));
        Map<String, Object> systemContent = Map.of(
                "parts", List.of(Map.of("text", systemInstruction == null ? "" : systemInstruction)));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        // canonical 생성은 결정성이 우선이라 더 낮은 temperature 를 쓸 수 있지만, 호출 측에서 schema 만 전달하므로
        // 공통 경로는 균형값으로 둔다. 결정성이 필요한 호출자는 별도 메서드를 추가해 override 한다.
        generationConfig.put("temperature", 0.0);
        generationConfig.put("maxOutputTokens", 2048);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", schema);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemContent);
        body.put("contents", List.of(userContent));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private static String firstAllowedModel(AppProperties.Llm.Gemini gemini) {
        return gemini == null ? "" : gemini.safeModels().stream().findFirst().orElse("");
    }

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

    // 호출 측이 사용자에게 명시적 에러를 띄우거나 폴백을 결정하는 단일 신호.
    public static class GeminiCallException extends RuntimeException {
        public GeminiCallException(String message, Throwable cause) {
            super(message, cause);
        }
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

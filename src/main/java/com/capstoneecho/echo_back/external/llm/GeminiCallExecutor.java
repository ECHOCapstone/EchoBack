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
    private final int maxOutputTokens;
    private final String thinkingLevel;
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
        this.maxOutputTokens = g == null ? 8192 : g.safeMaxOutputTokens();
        this.thinkingLevel = g == null ? "" : g.safeThinkingLevel();
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

    private Map<String, Object> buildBody(
            String systemInstruction, String userPrompt, Map<String, Object> schema) {
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt)));
        Map<String, Object> systemContent = Map.of(
                "parts", List.of(Map.of("text", systemInstruction == null ? "" : systemInstruction)));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        // Gemini 3.x 는 temperature/top_p/top_k 를 권장하지 않는다(기본값에 맞춰 최적화됨). 결정성은 sampling
        // 대신 시스템 프롬프트의 명시 규칙으로 확보한다. 따라서 sampling 파라미터는 보내지 않는다.
        // 정렬·교정이 긴 발화(맞춤학습 커스텀 문장 등)에서 구조화 출력 JSON 이 객체 중간에서 잘리면
        // (UnexpectedEndOfInput) 파싱 실패→폴백이 된다. 출력 상한을 넉넉히 둔다. (app.llm.gemini.max-output-tokens)
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        // 추론 수준(MINIMAL/LOW/MEDIUM/HIGH). 비우면 보내지 않아 모델별 기본값을 따른다. thinking 토큰을 줄이면
        // 지연·비용이 낮아지고 잘림 여유도 커진다. (3.x 는 thinking_budget 대신 thinking_level 을 쓴다.)
        if (!thinkingLevel.isBlank()) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel));
        }
        // 구조화 출력. responseFormat.text.mimeType 는 MIME 문자열이 아니라 enum 이라 v1beta generateContent
        // 에서 "application/json" 을 거부(400 INVALID_ARGUMENT)한다. 3.x 에서도 정상 동작하고 deprecated 가
        // 아닌 responseMimeType + responseSchema 를 사용한다.
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

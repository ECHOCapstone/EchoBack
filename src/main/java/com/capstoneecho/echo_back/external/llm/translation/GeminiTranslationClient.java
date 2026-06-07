package com.capstoneecho.echo_back.external.llm.translation;

import com.capstoneecho.echo_back.external.llm.LlmSettingKeys;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Gemini REST API 의 generateContent 를 호출해 영어 → 한국어 번역을 받아 온다.
// 구조화 출력(JSON 스키마) 대신 텍스트 응답을 쓴다 — 번역은 자연어 한 줄이라 스키마가 과하다.
// 여러 원문은 번호 매긴 한 프롬프트로 묶어 한 번에 처리한다 (Gemini 한 번 호출당 N 문장 → 비용 최소화).
// 응답은 "1. ko1\n2. ko2\n..." 형식을 기대하고, 정규식으로 번호별 라인을 매칭해 입력 순서대로 정렬한다.
@Component
public class GeminiTranslationClient implements TranslationClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiTranslationClient.class);

    // 결정적이고 짧은 출력을 위해 낮은 temperature 와 충분한 outputTokens 를 고정한다.
    private static final double TEMPERATURE = 0.2;
    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    // 응답 한 줄에서 "N. 본문" 형태를 분리한다. 번호 구분자는 . / ) / : / - 중 어느 것이든 받고,
    // Gemini 가 굵게 마커 (`**1.**` 또는 `**1**.`) 로 응답해도 통과하도록 선행 `**` 를 허용한다.
    private static final Pattern LINE_PATTERN = Pattern.compile("^\\**\\s*(\\d+)\\**\\s*[.):\\-]\\s*\\**(.+?)\\**\\s*$");

    private final RestClient restClient;
    private final SettingsService settings;
    private final String apiKey;
    private final String defaultModel;
    private final boolean available;

    public GeminiTranslationClient(
            AppProperties appProperties,
            SettingsService settings) {
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

    // 모델 기본값을 허용 모델 목록의 첫 항목에서 도출한다. yaml 의 models 목록이 단일 출처.
    private static String firstAllowedModel(AppProperties.Llm.Gemini gemini) {
        return gemini == null ? "" : gemini.safeModels().stream().findFirst().orElse("");
    }

    // apiKey 가 설정돼 호출 가능한 상태인지. DispatchingTranslationClient 가 라우팅 판단에 쓴다.
    public boolean isAvailable() {
        return available;
    }

    @Override
    public List<String> translate(List<String> sourceTexts) {
        if (sourceTexts == null || sourceTexts.isEmpty()) {
            return List.of();
        }
        String prompt = buildBatchPrompt(sourceTexts);
        String text = call(prompt);
        if (text == null || text.isBlank()) {
            return emptyOf(sourceTexts.size());
        }
        return parseLines(text, sourceTexts.size());
    }

    // 입력 N 문장에 1.~N. 번호를 매겨 한 프롬프트로 묶는다. 응답은 같은 번호 형식의 한국어 라인을 기대한다.
    private String buildBatchPrompt(List<String> sourceTexts) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("다음 영어 문장들을 자연스러운 한국어로 번역해 주세요.\n");
        sb.append("응답은 반드시 입력과 같은 번호로 시작하는 한 줄씩, 한국어 번역만 적습니다.\n");
        sb.append("따옴표 / 코드블록 / 영어 원문 / 추가 설명을 출력하지 마세요.\n\n");
        for (int i = 0; i < sourceTexts.size(); i++) {
            sb.append(i + 1).append(". ").append(sourceTexts.get(i)).append('\n');
        }
        return sb.toString();
    }

    // 응답 본문에서 "N. xxx" 패턴을 라인 단위로 매칭한다. 번호가 빠진 입력은 빈 문자열로 남는다.
    private List<String> parseLines(String raw, int expected) {
        List<String> result = new ArrayList<>(expected);
        for (int i = 0; i < expected; i++) {
            result.add("");
        }
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = LINE_PATTERN.matcher(trimmed);
            if (!m.find()) continue;
            int index;
            try {
                index = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignore) {
                continue;
            }
            if (index < 1 || index > expected) continue;
            // 같은 번호가 두 번 등장하면 첫 매칭만 유지해 예측 가능한 결과를 보장한다.
            if (!result.get(index - 1).isEmpty()) continue;
            result.set(index - 1, m.group(2).trim());
        }
        return result;
    }

    // Gemini generateContent 호출. 실패 시 null 을 돌려 호출 측이 폴백 (빈 결과) 으로 떨어진다.
    private String call(String userPrompt) {
        String model = settings.getOrDefault(LlmSettingKeys.GEMINI_MODEL, defaultModel);
        Map<String, Object> body = buildBody(userPrompt);
        try {
            GeminiResponse response = restClient.post()
                    .uri(uri -> uri
                            .path("/v1beta/models/{model}:generateContent")
                            .build(model))
                    // API 키는 헤더로 전달 — 쿼리 파라미터는 로그 / 프록시에 평문 노출 위험.
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            return extractText(response);
        } catch (RuntimeException ex) {
            log.warn("Gemini 번역 호출 실패 — 빈 결과 반환", ex);
            return null;
        }
    }

    // text-only 응답 모드. responseMimeType 없이 plain text 를 돌려받는다.
    private Map<String, Object> buildBody(String userPrompt) {
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt)));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(userContent));
        body.put("generationConfig", generationConfig);
        return body;
    }

    // 응답 구조: candidates[0].content.parts[*].text 들을 이어 붙여 평문으로 돌려준다.
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

    private static List<String> emptyOf(int size) {
        List<String> empty = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            empty.add("");
        }
        return empty;
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

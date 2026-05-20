package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
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

// Google Gemini REST API 로 가이던스를 생성하는 LlmClient 구현.
// 활성화 조건: app.llm.provider = gemini. apiKey 가 비어 있으면 부팅 시 즉시 실패한다.
// 호출 실패 / 빈 응답은 호출 측 (FeedbackService) 의 외부화된 폴백 문구가 받아낸다.
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "gemini")
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    // generateContent 요청에서 응답을 비결정적으로 흩뿌리지 않도록 낮은 temperature 를 고정한다.
    private static final double TEMPERATURE = 0.4;
    private static final int MAX_OUTPUT_TOKENS = 256;
    private static final RuleBasedFallback FALLBACK_TEMPLATE_GENERATOR = new RuleBasedFallback();

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiLlmClient(AppProperties appProperties) {
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
    public RecordingGuidance summarizeRecording(LlmContext context) {
        String prompt = """
                다음 발음 분석 결과를 바탕으로 한국어로 1~2문장의 따뜻한 피드백을 작성해 주세요.
                기술 용어나 음소 기호는 풀어 쓰고, 학습자가 다음에 어떤 부분을 신경 쓰면 좋을지 짚어 주세요.

                목표 문장: %s
                약점 음소: %s
                음소 오류 수: %d
                """.formatted(
                safe(context.targetText()),
                safePhoneme(context.weakPhoneme()),
                context.errors() == null ? 0 : context.errors().size());

        String guidance = callForText(prompt);
        // wrongWords 매핑은 모델이 일관되게 인덱스를 못 맞추는 경우가 잦아 규칙 기반 결과를 그대로 쓴다.
        List<WrongWord> wrongWords = FALLBACK_TEMPLATE_GENERATOR.wrongWords(context);
        if (guidance == null || guidance.isBlank()) {
            return null;
        }
        return new RecordingGuidance(guidance, wrongWords);
    }

    @Override
    public String summarizeFeedback(LlmContext context) {
        String prompt = """
                학습자에게 보낼 종합 발음 피드백을 한국어 1~2문장으로 작성해 주세요.
                구체적이고 실천 가능한 조언을 담아 주세요.

                목표 문장: %s
                약점 음소: %s
                """.formatted(safe(context.targetText()), safePhoneme(context.weakPhoneme()));
        return callForText(prompt);
    }

    @Override
    public String retryGuidance(LlmContext context) {
        String prompt = """
                학습자가 단어 한 개를 다시 따라 읽으려 합니다. 한국어 1~2문장으로 발음 팁을 알려 주세요.

                연습 단어: %s
                약점 음소: %s
                """.formatted(safe(context.targetText()), safePhoneme(context.weakPhoneme()));
        return callForText(prompt);
    }

    @Override
    public String suggestPracticeWord(LlmContext context) {
        String prompt = """
                약점 음소 "%s" 발음 연습에 좋은 영어 단어 한 개만 추천해 주세요.
                응답은 단어 자체만 영어로, 다른 설명 없이 출력하세요.
                """.formatted(safePhoneme(context.weakPhoneme()));
        String word = callForText(prompt);
        return word == null ? null : word.trim().split("\\s+")[0];
    }

    // generateContent 호출. 모든 예외는 WARN 로깅 후 null 로 떨어져 호출 측이 폴백을 쓰게 한다.
    private String callForText(String prompt) {
        Map<String, Object> body = buildBody(prompt);
        try {
            GeminiResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            return extractText(response);
        } catch (RuntimeException ex) {
            log.warn("Gemini call failed: {}", ex.getMessage());
            return null;
        }
    }

    private static Map<String, Object> buildBody(String prompt) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", List.of(textPart));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);
        return body;
    }

    // 응답 구조: candidates[0].content.parts[*].text 들을 이어 붙인다.
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

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String safePhoneme(String s) {
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

    // 단어 경계 기반 wrongWords 산출은 규칙 기반과 동일하므로 그 로직을 재사용한다.
    private static final class RuleBasedFallback {

        List<WrongWord> wrongWords(LlmContext context) {
            List<AnalyzeError> errors = context.errors();
            if (errors == null || errors.isEmpty()) {
                return List.of();
            }
            String[] words = splitWords(context.targetText());
            if (words.length == 0) {
                return List.of();
            }
            int[] boundaries = cumulative(context.g2pWords());
            if (boundaries.length == 0) {
                return List.of();
            }
            int totalPhonemes = boundaries[boundaries.length - 1];
            int boundedWords = Math.min(words.length, boundaries.length);
            List<WrongWord> out = new ArrayList<>();
            java.util.LinkedHashSet<Integer> seen = new java.util.LinkedHashSet<>();
            for (AnalyzeError error : errors) {
                Integer idx = error.canonicalIndex();
                if (idx == null || idx < 0 || idx >= totalPhonemes) {
                    continue;
                }
                int wordIdx = -1;
                for (int i = 0; i < boundaries.length; i++) {
                    if (idx < boundaries[i]) { wordIdx = i; break; }
                }
                if (wordIdx < 0 || wordIdx >= boundedWords) continue;
                String word = words[wordIdx].replaceAll("^\\p{Punct}+|\\p{Punct}+$", "");
                if (word.isBlank()) continue;
                if (seen.add(wordIdx)) out.add(new WrongWord(word, wordIdx));
            }
            return List.copyOf(out);
        }

        private static String[] splitWords(String s) {
            if (s == null) return new String[0];
            String t = s.trim();
            return t.isEmpty() ? new String[0] : t.split("\\s+");
        }

        private static int[] cumulative(List<com.capstoneecho.echo_back.external.modelserver.dto.G2pWord> words) {
            if (words == null || words.isEmpty()) return new int[0];
            int[] c = new int[words.size()];
            int running = 0;
            for (int i = 0; i < words.size(); i++) {
                List<String> ph = words.get(i).phonemes();
                running += ph == null ? 0 : ph.size();
                c[i] = running;
            }
            return c;
        }
    }
}

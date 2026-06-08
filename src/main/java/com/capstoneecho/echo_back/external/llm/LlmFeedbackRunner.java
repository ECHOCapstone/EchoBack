package com.capstoneecho.echo_back.external.llm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import tools.jackson.databind.ObjectMapper;

// Spring 컨텍스트 없이 Gemini generateContent 를 호출해 응답을 출력하는 CLI.
// 세 가지 모드를 지원한다:
//   1) --prompt "<자유 텍스트>"
//        -> 시스템 프롬프트 / 스키마 없이 평문 응답을 받는다.
//   2) retry <word> <perceived>
//        -> system.md + retry-feedback.md + retryFeedback() 스키마.
//        canonical 은 LLM 이 응답에 함께 생성한다.
//   3) step <chapterTitle> <targetText> <perceived>
//        -> system.md + step-feedback.md + stepFeedback() 스키마.
//        canonical 은 LLM 이 응답에 함께 생성한다.
//   4) comprehensive <chapterTitle> <chapterContent> <overallAccuracy> <dominantWeakPhoneme>
//        -> system.md + comprehensive-feedback.md + comprehensiveFeedback() 스키마.
//        stepSummaries / aggregatedErrors 는 비워서 호출하므로, 모델은 챕터 메타 정보만으로 추론한다.
//
// 환경 (앞에서부터 읽고, 뒤가 덮어쓴다): .env -> .env.local -> 프로세스 환경변수.
// .env.local 에 있는 'export ' 접두사와 따옴표는 자동으로 벗겨낸다.
//
// 키 매핑:
//   GEMINI_API_KEY            (필수)
//   APP_LLM_GEMINI_MODEL      (기본 gemini-2.0-flash)
//   APP_LLM_GEMINI_BASE_URL   (기본 https://generativelanguage.googleapis.com)
public final class LlmFeedbackRunner {

    private static final String DEFAULT_MODEL = "gemini-2.0-flash";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final double TEMPERATURE = 0.4;
    private static final int MAX_OUTPUT_TOKENS = 2048;

    private LlmFeedbackRunner() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        Properties env = loadEnv();
        String apiKey = require(env, "GEMINI_API_KEY");
        String model = orDefault(env, "APP_LLM_GEMINI_MODEL", DEFAULT_MODEL);
        String baseUrl = orDefault(env, "APP_LLM_GEMINI_BASE_URL", DEFAULT_BASE_URL);

        ObjectMapper mapper = new ObjectMapper();

        String mode = args[0];
        Map<String, Object> body = switch (mode) {
            case "--prompt", "prompt" -> {
                if (args.length < 2) {
                    printUsage();
                    System.exit(1);
                    yield Map.of();
                }
                yield freeFormBody(args[1]);
            }
            case "retry" -> {
                if (args.length < 3) {
                    printUsage();
                    System.exit(1);
                    yield Map.of();
                }
                yield retryBody(args[1], args[2]);
            }
            case "step" -> {
                if (args.length < 4) {
                    printUsage();
                    System.exit(1);
                    yield Map.of();
                }
                yield stepBody(args[1], args[2], args[3]);
            }
            case "comprehensive" -> {
                if (args.length < 5) {
                    printUsage();
                    System.exit(1);
                    yield Map.of();
                }
                yield comprehensiveBody(args[1], args[2], args[3], args[4]);
            }
            default -> {
                printUsage();
                System.exit(1);
                yield Map.of();
            }
        };

        System.err.println("[runLlmFeedback] model=" + model + " mode=" + mode);
        String responseJson = call(baseUrl, model, apiKey, body, mapper);
        System.out.println(prettyPrint(responseJson, mapper));
    }

    private static Map<String, Object> freeFormBody(String prompt) {
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt)));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(userContent));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private static Map<String, Object> retryBody(String word, String perceived) throws IOException {
        String userPrompt = loadPrompt("retry-feedback")
                .replace("{{word}}", word)
                .replace("{{perceived}}", perceived)
                .replace("{{canonicalWords}}", "(생략)")
                .replace("{{priorAttempts}}", "(없음)");
        return structuredBody(userPrompt, LlmJsonSchemas.retryFeedback());
    }

    private static Map<String, Object> stepBody(String chapterTitle, String targetText,
                                                String perceived) throws IOException {
        String userPrompt = loadPrompt("step-feedback")
                .replace("{{chapterTitle}}", chapterTitle)
                .replace("{{targetText}}", targetText)
                .replace("{{perceived}}", perceived)
                .replace("{{canonicalWords}}", "(생략)")
                .replace("{{priorAttempts}}", "(없음)");
        return structuredBody(userPrompt, LlmJsonSchemas.stepFeedback());
    }

    private static Map<String, Object> comprehensiveBody(String chapterTitle, String chapterContent,
                                                         String overallAccuracy, String dominantWeakPhoneme) throws IOException {
        String userPrompt = loadPrompt("comprehensive-feedback")
                .replace("{{chapterTitle}}", chapterTitle)
                .replace("{{chapterContent}}", chapterContent)
                .replace("{{overallAccuracy}}", overallAccuracy)
                .replace("{{dominantWeakPhoneme}}", dominantWeakPhoneme)
                .replace("{{stepSummaries}}", "(없음)")
                .replace("{{aggregatedErrors}}", "(없음)");
        return structuredBody(userPrompt, LlmJsonSchemas.comprehensiveFeedback());
    }

    private static Map<String, Object> structuredBody(String userPrompt, Map<String, Object> schema) throws IOException {
        String cleaned = userPrompt.replaceAll("\\{\\{[a-zA-Z0-9_]+}}", "");
        Map<String, Object> systemContent = Map.of(
                "parts", List.of(Map.of("text", loadPrompt("system"))));
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", cleaned)));

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

    private static String call(String baseUrl, String model, String apiKey,
                               Map<String, Object> body, ObjectMapper mapper) throws Exception {
        String bodyJson = mapper.writeValueAsString(body);
        URI uri = URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey);
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Gemini API " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private static String prettyPrint(String json, ObjectMapper mapper) {
        try {
            Object o = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return json;
        }
    }

    private static String loadPrompt(String key) throws IOException {
        String resource = "content/prompts/" + key + ".md";
        try (InputStream in = LlmFeedbackRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("prompt resource not found on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Properties loadEnv() throws IOException {
        Properties out = new Properties();
        loadDotenv(Path.of(".env"), out);
        loadDotenv(Path.of(".env.local"), out);
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            out.setProperty(e.getKey(), e.getValue());
        }
        return out;
    }

    private static void loadDotenv(Path path, Properties out) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2 &&
                    ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            out.setProperty(key, value);
        }
    }

    private static String require(Properties env, String key) {
        String v = env.getProperty(key);
        if (v == null || v.isBlank()) {
            System.err.println(key + " 가 환경변수 / .env / .env.local 어디에도 설정되지 않았습니다.");
            System.exit(1);
        }
        return v;
    }

    private static String orDefault(Properties env, String key, String def) {
        String v = env.getProperty(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  --prompt \"<자유 텍스트>\"");
        System.err.println("  retry         <word>          <perceived>");
        System.err.println("  step          <chapterTitle>  <targetText>          <perceived>");
        System.err.println("  comprehensive <chapterTitle>  <chapterContent>      <overallAccuracy>     <dominantWeakPhoneme>");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  ./gradlew runLlmFeedback --args=\"--prompt '안녕이라고 답해줘'\"");
        System.err.println("  ./gradlew runLlmFeedback --args=\"retry rabbit 'L AE B AH T'\"");
        System.err.println("  ./gradlew runLlmFeedback --args=\"step '오늘의 인사' 'I love rabbits' 'AY L AH V L AE B AH T S'\"");
        System.err.println("  ./gradlew runLlmFeedback --args=\"comprehensive '자음 R 연습' 'R 음소가 자주 등장하는 짧은 문장 모음' 72 R\"");
    }
}

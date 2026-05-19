package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.llm.GeminiLlmClient;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

// 모델 서버 없이 canonical / perceived 등 입력을 CLI 인자로 받아 GeminiLlmFeedbackGenerator
// 네 메서드 (step / unit / retry / practice-word) 를 그대로 호출하고 응답을 출력한다.
//
//   ./gradlew runLlmFeedback --args="step 'I have a red rabbit' 72.5 \
//     'AY HH AE V AH R EH D R AE B AH T' 'AY HH AE V AH L EH D L AE B AH T' \
//     'SUB:R:L:5,SUB:R:L:8'"
//
//   ./gradlew runLlmFeedback --args="unit '자음 R 연습' 65 R 'SUB:R:L:5,SUB:R:L:8'"
//   ./gradlew runLlmFeedback --args="retry rabbit 'R AE B AH T' 'L AE B AH T'"
//   ./gradlew runLlmFeedback --args="practice-word '자음 R 연습' R"
//
// 옵션:
//   --no-terms : prompts.yaml 의 <용어 목록> 데이터와 "용어 목록 인용" 지시를 빼고 호출.
//                아학편 데이터를 주지 않았을 때의 응답 품질을 비교할 때 쓴다.
//                예) ./gradlew runLlmFeedback --args="--no-terms unit '자음 R 연습' 65 R"
//
// LLM_API_KEY / LLM_MODEL / LLM_BASE_URL 은 backend/.env 에서 읽는다.
public class LlmFeedbackRunner {

    public static void main(String[] args) throws Exception {
        var stripTerms = false;
        var filtered = new ArrayList<String>();
        for (var a : args) {
            if ("--no-terms".equals(a)) stripTerms = true;
            else filtered.add(a);
        }
        args = filtered.toArray(new String[0]);

        if (args.length < 1) {
            usage();
            return;
        }

        var env = loadEnv();
        var apiKey = env.getProperty("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("LLM_API_KEY 가 .env 에 비어 있습니다.");
            System.exit(1);
        }
        var baseUrl = env.getProperty("LLM_BASE_URL", "https://generativelanguage.googleapis.com/v1beta");
        var model = env.getProperty("LLM_MODEL", "gemini-2.0-flash");

        var resource = new ClassPathResource("prompts.yaml");
        PromptTemplates templates = stripTerms
                ? new PromptTemplates(resource) {
                    @Override public String system() {
                        return stripTermsBlock(super.system());
                    }
                    @Override public String prompt(String key) {
                        return stripTermsInstructions(super.prompt(key));
                    }
                }
                : new PromptTemplates(resource);
        var builder = new PronunciationPromptBuilder(templates);
        var client = new GeminiLlmClient(baseUrl, apiKey, model, JsonMapper.builder().build());
        var generator = new GeminiLlmFeedbackGenerator(client, builder);

        System.out.println("[model] " + model + (stripTerms ? "  [용어 목록 제외]" : ""));
        System.out.println();

        switch (args[0]) {
            case "step" -> runStep(generator, args);
            case "unit" -> runUnit(generator, args);
            case "retry" -> runRetry(generator, args);
            case "practice-word" -> runPracticeWord(generator, args);
            default -> {
                System.err.println("Unknown mode: " + args[0]);
                usage();
            }
        }
    }

    // step <targetText> <stepScore> <canonical> <perceived> [errors]
    private static void runStep(GeminiLlmFeedbackGenerator g, String[] args) {
        if (args.length < 5) { usage(); return; }
        var target = args[1];
        var score = Double.parseDouble(args[2]);
        var canonical = splitPhonemes(args[3]);
        var perceived = splitPhonemes(args[4]);
        var errors = args.length >= 6 ? parseErrors(args[5]) : List.<PhonemeErrorResponse>of();
        var result = g.stepGuidance(target, score, perceived, canonical, errors);
        System.out.println("message:    " + result.message());
        System.out.println("wrongWords: " + result.wrongWords());
    }

    // unit <unitTitle> <accuracy> <weakPhoneme> [errors]
    private static void runUnit(GeminiLlmFeedbackGenerator g, String[] args) {
        if (args.length < 4) { usage(); return; }
        var errors = args.length >= 5 ? parseErrors(args[4]) : List.<PhonemeErrorResponse>of();
        var result = g.unitGuidance(args[1], Double.parseDouble(args[2]), args[3], errors);
        System.out.println(result);
    }

    // retry <practiceWord> <canonical> <perceived>
    private static void runRetry(GeminiLlmFeedbackGenerator g, String[] args) {
        if (args.length < 4) { usage(); return; }
        var canonical = splitPhonemes(args[2]);
        var perceived = splitPhonemes(args[3]);
        var result = g.evaluateRetry(args[1], perceived, canonical);
        System.out.println("correct:  " + result.correct());
        System.out.println("guidance: " + result.guidance());
    }

    // practice-word <unitTitle> <weakPhoneme>
    private static void runPracticeWord(GeminiLlmFeedbackGenerator g, String[] args) {
        if (args.length < 3) { usage(); return; }
        var result = g.recommendPracticeWord(args[1], args[2]);
        System.out.println(result.isBlank() ? "(빈 응답 — 호출자가 자체 fallback 으로 떨어짐)" : result);
    }

    // system 프롬프트에서 <용어 목록> 블록 이후를 통째로 잘라낸다.
    private static String stripTermsBlock(String s) {
        var idx = s.indexOf("<용어 목록>");
        return idx >= 0 ? s.substring(0, idx).stripTrailing() : s;
    }

    // 각 응답 directive 에서 "용어 목록" 이 언급된 줄을 제거한다 (unit.prompt 의 인용 지시 등).
    private static String stripTermsInstructions(String s) {
        var sb = new StringBuilder();
        for (var line : s.split("\\R")) {
            if (line.contains("용어 목록")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static List<String> splitPhonemes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.trim().split("\\s+")).toList();
    }

    // "OP:CANON:PERC:INDEX,OP:CANON:PERC:INDEX,..." → List<PhonemeErrorResponse>
    private static List<PhonemeErrorResponse> parseErrors(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        var list = new ArrayList<PhonemeErrorResponse>();
        for (var item : raw.split(",")) {
            var parts = item.trim().split(":");
            if (parts.length < 4) continue;
            list.add(new PhonemeErrorResponse(parts[0], parts[1], parts[2], Integer.parseInt(parts[3])));
        }
        return list;
    }

    private static Properties loadEnv() throws Exception {
        var props = new Properties();
        var candidates = List.of(Path.of(".env"), Path.of("backend/.env"));
        for (var p : candidates) {
            if (Files.exists(p)) {
                try (var in = new FileInputStream(p.toFile())) {
                    props.load(in);
                }
                return props;
            }
        }
        return props;
    }

    private static void usage() {
        System.out.println("""
                Usage:
                  [--no-terms] step <targetText> <stepScore> <canonical> <perceived> [errors]
                  [--no-terms] unit <unitTitle> <accuracy> <weakPhoneme> [errors]
                  [--no-terms] retry <practiceWord> <canonical> <perceived>
                  [--no-terms] practice-word <unitTitle> <weakPhoneme>

                  --no-terms : <용어 목록> 데이터 + "용어 목록 인용" 지시를 빼고 호출
                  phonemes   : 공백 구분, 예) "AY HH AE V AH R EH D"
                  errors     : OP:CANON:PERC:INDEX 콤마 구분, 예) "SUB:R:L:5,SUB:R:L:8"
                """);
    }
}

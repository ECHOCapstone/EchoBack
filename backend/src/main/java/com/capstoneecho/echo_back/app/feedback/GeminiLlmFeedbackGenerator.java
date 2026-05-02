package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

// Gemini API 로 한국어 안내 문장을 생성한다. step / unit 은 자유 텍스트, retry / practice-word 는
// prompts.yaml 의 schema 로 JSON 응답을 강제받아 키별로 꺼낸다. 호출 실패나 응답이 비면 정적
// 안전망 문장으로 떨어진다. app.llm.provider=gemini 일 때만 빈으로 등록된다.
@Component
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "gemini")
class GeminiLlmFeedbackGenerator implements LlmFeedbackGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmFeedbackGenerator.class);

    private final LlmClient llmClient;
    private final PronunciationPromptBuilder promptBuilder;

    GeminiLlmFeedbackGenerator(LlmClient llmClient, PronunciationPromptBuilder promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String stepGuidance(
            String targetText,
            double stepScore,
            List<String> perceived,
            List<String> canonical,
            List<PhonemeErrorResponse> errors
    ) {
        var prompt = promptBuilder.buildStepPrompt(targetText, stepScore, perceived, canonical, errors);
        return invoke(prompt, "발음 결과를 확인했어요. 다시 한 번 또렷하게 시도해 봐요.");
    }

    @Override
    public String unitGuidance(
            String unitTitle,
            double accuracy,
            String weakPhoneme,
            List<PhonemeErrorResponse> errors
    ) {
        var prompt = promptBuilder.buildUnitPrompt(unitTitle, accuracy, weakPhoneme, errors);
        // 정확도 수치는 프론트가 별도 라벨로 노출하므로 fallback 문장에는 포함하지 않는다.
        return invoke(prompt, "가장 자주 틀린 음소를 다시 한 번 연습해 보세요.");
    }

    // prompts.yaml 의 retry.schema 가 정의한 {correct, guidance} JSON 으로 응답을 받는다.
    // 키 이름이 yaml 에서 바뀌면 여기서 꺼내는 path 도 같이 바꿔야 한다.
    @Override
    public RetryEvaluation evaluateRetry(
            String practiceWord,
            List<String> perceived,
            List<String> canonical
    ) {
        var prompt = promptBuilder.buildRetryPrompt(practiceWord, perceived, canonical);
        var schema = promptBuilder.retrySchema();
        try {
            var json = llmClient.generateJson(prompt, schema);
            if (json == null) return fallback(practiceWord);
            boolean correct = json.path("correct").asBoolean(false);
            String guidance = json.path("guidance").asString("");
            if (guidance.isBlank()) guidance = defaultGuidance(correct, practiceWord);
            return new RetryEvaluation(correct, guidance);
        } catch (Exception e) {
            log.warn("LLM 호출 실패, 기본 응답으로 대체합니다: {}", e.getMessage());
            return fallback(practiceWord);
        }
    }

    private static RetryEvaluation fallback(String practiceWord) {
        return new RetryEvaluation(false, defaultGuidance(false, practiceWord));
    }

    private static String defaultGuidance(boolean correct, String practiceWord) {
        return correct
                ? practiceWord + " 발음이 정확해졌어요. 다음 단계로 넘어가도 좋아요."
                : practiceWord + " 발음을 한 번 더 또렷하게 굴려 보세요.";
    }

    // prompts.yaml 의 practice-word.schema 가 정의한 {word} JSON 으로 응답을 받는다.
    // 비어 있거나 실패면 빈 문자열을 돌려 PracticeWordResolver 가 자체 fallback 으로 떨어진다.
    @Override
    public String recommendPracticeWord(String unitTitle, String weakPhoneme) {
        if (weakPhoneme == null || weakPhoneme.isBlank()) return "";
        var prompt = promptBuilder.buildPracticeWordPrompt(unitTitle, weakPhoneme);
        var schema = promptBuilder.practiceWordSchema();
        try {
            var json = llmClient.generateJson(prompt, schema);
            if (json == null) return "";
            return sanitizeWord(json.path("word").asString(""));
        } catch (Exception e) {
            log.warn("LLM 호출 실패, 기본 응답으로 대체합니다: {}", e.getMessage());
            return "";
        }
    }

    // 모델이 따옴표나 공백을 섞어 보낼 가능성에 대비해 a-z 외 문자를 떼고 첫 토큰만 단어로 본다.
    private static String sanitizeWord(String raw) {
        var cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", " ").trim();
        if (cleaned.isEmpty()) return "";
        return cleaned.split("\\s+")[0];
    }

    private String invoke(String prompt, String fallback) {
        try {
            var response = llmClient.generate(prompt);
            if (response == null) return fallback;
            var content = response.content();
            return content != null && !content.isBlank() ? content.trim() : fallback;
        } catch (Exception e) {
            log.warn("LLM 호출 실패, 기본 응답으로 대체합니다: {}", e.getMessage());
            return fallback;
        }
    }
}

package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

// Gemini API 로 한국어 안내 문장을 생성한다. 호출이 실패하거나 빈 응답이 오면 미리 준비한
// 정적 문장으로 떨어진다. app.llm.provider=gemini 일 때만 빈으로 등록된다.
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

    // LLM 이 첫 줄에 PASS / FAIL, 둘째 줄에 한국어 한 문장을 적어 보내도록 요구하고,
    // 응답을 두 줄로 갈라 RetryEvaluation 으로 묶는다. 호출이나 파싱이 실패하면
    // FAIL + 안전망 문장으로 떨어진다.
    @Override
    public RetryEvaluation evaluateRetry(
            String practiceWord,
            List<String> perceived,
            List<String> canonical
    ) {
        var prompt = promptBuilder.buildRetryPrompt(practiceWord, perceived, canonical);
        try {
            var response = llmClient.generate(prompt);
            if (response == null || response.content() == null || response.content().isBlank()) {
                return fallback(practiceWord);
            }
            return parse(response.content(), practiceWord);
        } catch (Exception e) {
            log.warn("LLM 호출 실패, 기본 응답으로 대체합니다: {}", e.getMessage());
            return fallback(practiceWord);
        }
    }

    private static RetryEvaluation parse(String raw, String practiceWord) {
        var lines = raw.trim().split("\\R", 2);
        var verdict = lines[0].trim().toUpperCase(Locale.ROOT);
        boolean correct = verdict.startsWith("PASS");
        String guidance = lines.length > 1 && !lines[1].isBlank()
                ? lines[1].trim()
                : defaultGuidance(correct, practiceWord);
        return new RetryEvaluation(correct, guidance);
    }

    private static RetryEvaluation fallback(String practiceWord) {
        return new RetryEvaluation(false, defaultGuidance(false, practiceWord));
    }

    private static String defaultGuidance(boolean correct, String practiceWord) {
        return correct
                ? practiceWord + " 발음이 정확해졌어요. 다음 단계로 넘어가도 좋아요."
                : practiceWord + " 발음을 한 번 더 또렷하게 굴려 보세요.";
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

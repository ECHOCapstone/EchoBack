package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// LlmFeedbackGenerator 의 외부 LLM 기반 구현체.
// PromptBuilder 가 만든 한국어 코칭 프롬프트를 LlmClient 에 전달하고, 호출 실패·빈 응답·예외 상황은
// 정적 fallback 문장으로 안전하게 흡수한다. 재연습 단어 결정은 PracticeWordResolver 가 책임지므로
// 본 구현은 음소→단어 매핑을 가지지 않는다 (SRP).
//
// 빈 등록은 app.llm.provider=gemini 일 때만 일어나며, RuleBased 와 양립 불가.
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

    @Override
    public String retryGuidance(
            String practiceWord,
            boolean correct,
            List<String> perceived,
            List<String> canonical
    ) {
        var prompt = promptBuilder.buildRetryPrompt(practiceWord, correct, perceived, canonical);
        var fallback = correct
                ? practiceWord + " 발음이 정확해졌어요. 다음 단계로 넘어가도 좋아요."
                : practiceWord + " 발음을 한 번 더 또렷하게 굴려 보세요.";
        return invoke(prompt, fallback);
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

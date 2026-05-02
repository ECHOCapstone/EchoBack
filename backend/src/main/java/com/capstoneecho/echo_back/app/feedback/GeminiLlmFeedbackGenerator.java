package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import com.capstoneecho.echo_back.app.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// LlmFeedbackGenerator 의 외부 LLM 기반 구현체.
// PromptBuilder 가 만든 한국어 코칭 프롬프트를 LlmClient 에 전달하고,
// 호출 실패·빈 응답·예외 상황은 RuleBasedLlmFeedbackGenerator 와 동일한 폴백으로 흡수한다.
//
// 빈 등록은 app.llm.provider=gemini 일 때만 일어나며, RuleBased 와 양립 불가.
@Component
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "gemini")
class GeminiLlmFeedbackGenerator implements LlmFeedbackGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmFeedbackGenerator.class);

    private static final String DEFAULT_PRACTICE_WORD = "rabbit";
    // 음소 → 추천 재연습 단어. 매핑이 없으면 DEFAULT_PRACTICE_WORD 로 폴백.
    private static final Map<String, String> WEAK_TO_PRACTICE_WORD = Map.ofEntries(
            Map.entry("r", "rabbit"),
            Map.entry("l", "light"),
            Map.entry("v", "vest"),
            Map.entry("b", "best"),
            Map.entry("th", "think"),
            Map.entry("dh", "this"),
            Map.entry("sh", "shoes"),
            Map.entry("zh", "measure"),
            Map.entry("ng", "song")
    );

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
    public Generated generate(
            String unitTitle,
            double accuracy,
            String weakPhoneme,
            List<PhonemeErrorResponse> errors
    ) {
        var prompt = promptBuilder.buildUnitPrompt(unitTitle, accuracy, weakPhoneme, errors);
        var practiceWord = WEAK_TO_PRACTICE_WORD.getOrDefault(
                weakPhoneme != null ? weakPhoneme.toLowerCase() : "",
                DEFAULT_PRACTICE_WORD
        );
        var fallback = String.format("이번 학습 정확도는 %.1f점이에요. 가장 자주 틀린 음소를 다시 한 번 연습해 보세요.", accuracy);
        return new Generated(invoke(prompt, fallback), practiceWord);
    }

    @Override
    public Generated retryGuidance(
            String practiceWord,
            boolean correct,
            List<String> perceived,
            List<String> canonical
    ) {
        var prompt = promptBuilder.buildRetryPrompt(practiceWord, correct, perceived, canonical);
        var fallback = correct
                ? practiceWord + " 발음이 정확해졌어요. 다음 단계로 넘어가도 좋아요."
                : practiceWord + " 발음을 한 번 더 또렷하게 굴려 보세요.";
        return new Generated(invoke(prompt, fallback), practiceWord);
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

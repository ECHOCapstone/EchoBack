package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * LLM 호출 없이 룰 기반으로 가이던스를 생성하는 기본 {@link LlmClient} 구현.
 *
 * <p>활성 조건: {@code app.llm.provider=rule-based} 또는 미설정 (기본).
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedLlmFeedbackGenerator implements LlmClient {

    private static final String DEFAULT_RECORDING_GUIDANCE = "발음을 더 또렷하게 따라 읽어 보세요.";
    private static final String DEFAULT_FEEDBACK_GUIDANCE = "꾸준한 연습이 발음 개선에 도움이 됩니다.";
    private static final String DEFAULT_RETRY_GUIDANCE = "해당 단어를 한 번 더 천천히 따라 읽어 보세요.";
    private static final String DEFAULT_PRACTICE_WORD = "the";

    @Override
    public RecordingGuidance summarizeRecording(LlmContext context) {
        return new RecordingGuidance(DEFAULT_RECORDING_GUIDANCE, resolveWrongWords(context));
    }

    @Override
    public String summarizeFeedback(LlmContext context) {
        return DEFAULT_FEEDBACK_GUIDANCE;
    }

    @Override
    public String retryGuidance(LlmContext context) {
        return DEFAULT_RETRY_GUIDANCE;
    }

    @Override
    public String suggestPracticeWord(LlmContext context) {
        String[] words = splitWords(context.targetText());
        if (words.length > 0) {
            String first = stripPunctuation(words[0]);
            if (!first.isBlank()) {
                return first;
            }
        }
        return DEFAULT_PRACTICE_WORD;
    }

    private List<String> resolveWrongWords(LlmContext context) {
        List<AnalyzeError> errors = context.errors();
        if (errors.isEmpty()) {
            return List.of();
        }
        String[] words = splitWords(context.targetText());
        if (words.length == 0) {
            return List.of();
        }
        int canonicalSize = context.canonical().size();
        if (canonicalSize <= 0) {
            return List.of();
        }

        LinkedHashSet<String> hits = new LinkedHashSet<>();
        for (AnalyzeError error : errors) {
            Integer idx = error.canonicalIndex();
            if (idx == null || idx < 0 || idx >= canonicalSize) {
                continue;
            }
            int wordIdx = (int) Math.min((long) words.length - 1, (long) idx * words.length / canonicalSize);
            String word = stripPunctuation(words[wordIdx]);
            if (!word.isBlank()) {
                hits.add(word);
            }
        }
        return List.copyOf(hits);
    }

    private static String[] splitWords(String targetText) {
        if (targetText == null) {
            return new String[0];
        }
        String trimmed = targetText.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    private static String stripPunctuation(String word) {
        return word.replaceAll("^\\p{Punct}+|\\p{Punct}+$", "");
    }
}

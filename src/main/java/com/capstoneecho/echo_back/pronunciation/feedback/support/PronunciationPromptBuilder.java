package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PronunciationPromptBuilder {

    public LlmContext buildAggregateContext(
            String targetText,
            Collection<Recording> recordings,
            List<AnalyzeError> aggregatedErrors,
            String weakPhoneme) {
        List<String> perceived = collectFirstPerceived(recordings);
        return LlmContext.builder()
                .targetText(targetText == null ? "" : targetText)
                .perceived(perceived)
                .canonical(List.of())
                .errors(aggregatedErrors == null ? List.of() : aggregatedErrors)
                .weakPhoneme(weakPhoneme)
                .build();
    }

    public LlmContext buildRetryContext(
            String word,
            List<String> perceived,
            List<String> canonical,
            List<AnalyzeError> errors,
            String weakPhoneme) {
        return LlmContext.builder()
                .targetText(word == null ? "" : word)
                .perceived(perceived)
                .canonical(canonical)
                .errors(errors)
                .weakPhoneme(weakPhoneme)
                .build();
    }

    private static List<String> collectFirstPerceived(Collection<Recording> recordings) {
        if (recordings == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Recording r : recordings) {
            String perceived = r.getPerceived();
            if (perceived == null || perceived.isBlank()) {
                continue;
            }
            for (String token : perceived.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    result.add(token);
                }
            }
        }
        return result;
    }
}

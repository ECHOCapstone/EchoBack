package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import org.springframework.stereotype.Component;

@Component
public class PracticeWordResolver {

    public static final String DEFAULT_PRACTICE_WORD = "the";

    private final LlmClient llmClient;

    public PracticeWordResolver(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String resolve(Script script, String weakPhoneme, LlmContext context) {
        if (script != null) {
            String seeded = script.getPracticeWord();
            if (seeded != null && !seeded.isBlank()) {
                return seeded;
            }
        }
        String suggested = null;
        try {
            suggested = llmClient.suggestPracticeWord(
                    context == null
                            ? LlmContext.builder().weakPhoneme(weakPhoneme).build()
                            : context);
        } catch (RuntimeException ignored) {
            // ignored — fall through to default
        }
        if (suggested != null && !suggested.isBlank()) {
            return suggested;
        }
        return DEFAULT_PRACTICE_WORD;
    }
}

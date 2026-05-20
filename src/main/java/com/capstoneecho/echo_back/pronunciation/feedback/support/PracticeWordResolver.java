package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import org.springframework.stereotype.Component;

// 연습 단어를 결정한다: 챕터에 미리 박힌 단어 → LLM 추천 → app.gamification 폴백 순.
@Component
public class PracticeWordResolver {

    private final LlmClient llmClient;
    private final String defaultPracticeWord;

    public PracticeWordResolver(LlmClient llmClient, AppProperties appProperties) {
        this.llmClient = llmClient;
        AppProperties.Gamification g = appProperties.gamification();
        this.defaultPracticeWord = g == null ? "the" : g.defaultPracticeWord();
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
            // LLM 폴백.
        }
        if (suggested != null && !suggested.isBlank()) {
            return suggested;
        }
        return defaultPracticeWord;
    }

    // 외부에서 폴백 단어가 필요할 때 (예: 재시도 흐름) 노출하는 단일 접근자.
    public String defaultWord() {
        return defaultPracticeWord;
    }
}

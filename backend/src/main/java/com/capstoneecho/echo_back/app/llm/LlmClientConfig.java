package com.capstoneecho.echo_back.app.llm;

import com.capstoneecho.echo_back.app.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// app.llm.provider=gemini 일 때만 GeminiLlmClient 를 빈으로 등록한다. 그 외에는 LlmClient 빈이
// 아예 없어서 RuleBasedLlmFeedbackGenerator 가 자동으로 활성화된다.
@Configuration
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "gemini")
class LlmClientConfig {

    @Bean
    LlmClient llmClient(AppProperties properties) {
        var llm = properties.llm();
        return new GeminiLlmClient(llm.apiKey(), llm.model());
    }
}

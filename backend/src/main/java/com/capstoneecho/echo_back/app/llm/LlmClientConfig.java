package com.capstoneecho.echo_back.app.llm;

import com.capstoneecho.echo_back.app.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// LlmClient 빈은 외부 LLM 사용을 명시한 경우에만 등록된다.
//   app.llm.provider == "gemini"  → GeminiLlmClient 등록
//   그 외 (rule-based 등)            → 등록하지 않음 → 규칙 기반 fallback 으로 자동 분기
//
// API 키가 비어 있으면 빈을 등록하더라도 호출 시 Gemini 가 거부하므로,
// LlmFeedbackGenerator 분기에서 동일하게 provider 키만으로 선택하면 충분하다.
@Configuration
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "gemini")
class LlmClientConfig {

    @Bean
    LlmClient llmClient(AppProperties properties) {
        var llm = properties.llm();
        return new GeminiLlmClient(llm.apiKey(), llm.model());
    }
}

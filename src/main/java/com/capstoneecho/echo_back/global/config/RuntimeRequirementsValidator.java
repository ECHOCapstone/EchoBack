package com.capstoneecho.echo_back.global.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// prod profile 부팅 시 운영 필수 설정값 (외부 서비스 endpoint / 시크릿) 이 누락됐는지 한 곳에서 검증한다.
// 누락 항목을 모아 한 번에 IllegalStateException 으로 던져 부팅을 거절한다 — 운영 사고를 부팅 이전에 차단한다.
@Component
@Profile("prod")
public class RuntimeRequirementsValidator {

    private final AppProperties appProperties;
    private final String llmProvider;

    public RuntimeRequirementsValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
        AppProperties.Llm llm = appProperties.llm();
        this.llmProvider = llm == null ? "" : (llm.provider() == null ? "" : llm.provider());
    }

    @PostConstruct
    void validate() {
        List<String> missing = new ArrayList<>();

        AppProperties.Jwt jwt = appProperties.jwt();
        if (jwt == null || jwt.secret() == null || jwt.secret().isBlank()) {
            missing.add("app.jwt.secret (JWT_SECRET)");
        }

        AppProperties.ModelServer ms = appProperties.modelServer();
        if (ms == null || ms.baseUrl() == null || ms.baseUrl().isBlank()) {
            missing.add("app.model-server.base-url (MODEL_SERVER_BASE_URL)");
        }
        if (ms == null || ms.timeoutMs() <= 0) {
            missing.add("app.model-server.timeout-ms (MODEL_SERVER_TIMEOUT_MS)");
        }

        // Gemini provider 활성 시에만 키를 강제한다 — rule-based 운영도 허용.
        if ("gemini".equalsIgnoreCase(llmProvider)) {
            AppProperties.Llm.Gemini g = appProperties.llm() == null ? null : appProperties.llm().gemini();
            if (g == null || g.apiKey() == null || g.apiKey().isBlank()) {
                missing.add("app.llm.gemini.api-key (GEMINI_API_KEY)");
            }
        }

        AppProperties.Member member = appProperties.member();
        if (member == null || member.retention() == null) {
            missing.add("app.member.retention.grace-days");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "운영 필수 설정 누락 — 부팅 거절: " + String.join(", ", missing));
        }
    }
}

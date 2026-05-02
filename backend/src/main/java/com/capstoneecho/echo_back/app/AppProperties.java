package com.capstoneecho.echo_back.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// application.yaml 의 app.* 설정을 타입-세이프로 한곳에 묶는 단일 진입점.
// 새로운 app 설정 추가 시 여기에만 필드를 정의하면 다른 컴포넌트가 안전하게 주입받는다.
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        ModelServer modelServer,
        Scoring scoring,
        Storage storage,
        Llm llm
) {

    public record Jwt(String secret, long expireMs) {}

    public record Cors(List<String> allowedOrigins) {}

    public record ModelServer(String baseUrl, long timeoutMs) {}

    // alpha 는 정확도 가중치, 1-alpha 는 모델 confidence(peak softmax 평균) 가중치.
    public record Scoring(double alpha) {}

    public record Storage(String recordingDir) {}

    // provider 는 LlmFeedbackGenerator 구현체 선택 키. apiKey 가 비어 있으면
    // gemini 를 골라도 빈이 등록되지 않아 자동으로 rule-based 로 떨어진다.
    public record Llm(String provider, String apiKey, String model) {}
}

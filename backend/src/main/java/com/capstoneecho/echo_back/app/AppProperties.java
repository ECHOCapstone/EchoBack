package com.capstoneecho.echo_back.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// application.yaml 의 app.* 설정을 타입 세이프하게 매핑한다.
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        ModelServer modelServer,
        Scoring scoring,
        Storage storage,
        Llm llm,
        Reward reward,
        Badge badge,
        Time time
) {

    public record Jwt(String secret, long expireMs) {}

    public record Cors(List<String> allowedOrigins) {}

    public record ModelServer(String baseUrl, long timeoutMs) {}

    // alpha 는 정확도 가중치, 1-alpha 는 모델 confidence(peak softmax 평균) 가중치.
    public record Scoring(double alpha) {}

    public record Storage(String recordingDir) {}

    // provider 가 gemini 일 때만 외부 LLM 클라이언트가 등록되고, 그 외에는 규칙 기반으로 떨어진다.
    public record Llm(String provider, String apiKey, String model) {}

    // 한 챕터 학습을 끝냈을 때 사용자에게 지급할 EXP.
    public record Reward(int completionExp) {}

    // 배지 합격 임계값. 운영 중 밸런싱을 자주 만지므로 코드 상수 대신 yaml 로 노출한다.
    public record Badge(
            double masterThreshold,
            double perfectThreshold,
            int tongueTwisterGoal,
            int sessionMasterGoal
    ) {}

    // streak / 출석 캘린더 계산의 기준 시간대. 한국 사용자가 자정을 넘기는 순간이 streak 갱신
    // 시점이 되도록 KST 를 기본값으로 잡는다.
    public record Time(String zoneId) {}
}

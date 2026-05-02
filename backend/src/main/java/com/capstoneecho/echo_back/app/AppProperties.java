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
        Llm llm,
        Reward reward,
        Badge badge
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

    // 학습 완료 시 지급되는 보상 정책. 게임 밸런싱이라 운영 환경에서 자주 조정될 가능성이 높아
    // 코드 상수가 아닌 외부 설정으로 노출한다.
    public record Reward(int completionExp) {}

    // 배지 평가 임계값 정책. 게임 밸런싱이라 운영 환경에서 자주 조정될 가능성이 높아
    // BadgePolicy 의 코드 상수 대신 외부 설정으로 노출한다.
    //   masterThreshold     - 챕터 마스터 배지 합격 정확도
    //   perfectThreshold    - "완벽한 한 판" 배지 합격 정확도
    //   tongueTwisterGoal   - 잰말놀이 N회 완료 배지 임계 횟수
    //   sessionMasterGoal   - 맞춤 학습 마스터 배지 임계 세션 수
    public record Badge(
            double masterThreshold,
            double perfectThreshold,
            int tongueTwisterGoal,
            int sessionMasterGoal
    ) {}
}

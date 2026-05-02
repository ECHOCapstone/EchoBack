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
        Time time,
        Auth auth,
        Feedback feedback,
        Session session,
        Stats stats
) {

    public record Jwt(String secret, long expireMs) {}

    public record Cors(List<String> allowedOrigins) {}

    public record ModelServer(String baseUrl, long timeoutMs) {}

    // alpha 는 정확도 가중치, 1-alpha 는 모델 confidence(peak softmax 평균) 가중치.
    public record Scoring(double alpha) {}

    public record Storage(String recordingDir) {}

    // provider 가 gemini 이고 apiKey 가 채워져 있을 때만 외부 LLM 이 활성화되고, 그 외에는
    // RuleBasedLlmFeedbackGenerator 가 안전망으로 동작한다.
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

    // 시연용 구글 OAuth 가짜 계정. 실제 OAuth 가 붙기 전까지 한정된 임시 데이터라 코드가 아닌
    // 외부 설정으로 두어 운영 환경마다 다른 식별자를 쓸 수 있게 한다.
    public record Auth(DemoGoogle demoGoogle) {}

    public record DemoGoogle(String username, String email, String nickname, String password) {}

    // 발음 코칭 정책.
    //   stepThreshold        : step 점수 분기 (pass / ok / 그 미만) 임계값
    //   defaultPracticeWord  : Script.practiceWord 도, 음소 매핑도 결정 못 했을 때의 마지막 기본값
    public record Feedback(StepThreshold stepThreshold, String defaultPracticeWord) {}

    public record StepThreshold(double pass, double ok) {}

    // 사용자 맞춤 학습 세션 정책. sentenceMinLength 미만의 문장 조각은 앞 조각에 흡수된다.
    public record Session(int sentenceMinLength) {}

    // 통계 화면 표시 정책. 지난 한 주 오류 음소 중 상위 N 개만 노출한다.
    public record Stats(int weeklyTopN) {}
}

package com.capstoneecho.echo_back.global.config;

import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// application*.yaml 의 app.* 키를 도메인별 nested record 로 노출하는 단일 출처.
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        ModelServer modelServer,
        Llm llm,
        Storage storage,
        Tts tts,
        Stats stats,
        Auth auth,
        Gamification gamification,
        Messages messages,
        OAuth2 oauth2
) {

    public record Jwt(String secret, long expirationMs) {}

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAgeSec
    ) {}

    public record ModelServer(String baseUrl, long timeoutMs) {}

    // provider 식별자 ("rule-based" | "gemini") 와 provider 별 자격증명 / 엔드포인트.
    // gemini.apiKey 는 환경 변수 (GEMINI_API_KEY) 로 주입한다 — yaml 평문 금지.
    public record Llm(String provider, Gemini gemini) {

        public record Gemini(String apiKey, String model, String baseUrl, long timeoutMs) {}
    }

    public record Storage(String localRoot) {}

    public record Tts(String provider) {}

    // 통계 / 출석 집계에 쓰는 타임존과 배지 정의를 묶는다.
    public record Stats(String zone, List<Badge> badges) {

        // zone 미설정 / blank 면 시스템 zone 으로 폴백한다.
        public ZoneId resolvedZone() {
            return (zone == null || zone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(zone);
        }

        public List<Badge> safeBadges() {
            return badges == null ? List.of() : badges;
        }

        // condition 은 BadgePolicy 가 해석하는 enum-like 식별자 (FIRST_FEEDBACK, STREAK).
        public record Badge(String id, String name, String condition, int threshold) {}
    }

    // OAuth2 시연 사용자 식별자 외부화. 환경별 yaml 에서 다른 값을 주입한다.
    public record Auth(DemoGoogle demoGoogle) {

        public record DemoGoogle(String email, String nickname) {}
    }

    // 실제 Google OAuth2 로그인 성공 / 실패 후 브라우저를 보낼 프론트엔드 URL.
    // 토큰은 frontendRedirectUri 뒤에 URL fragment (#token=...&expiresIn=...) 로 붙는다.
    public record OAuth2(String frontendRedirectUri, String frontendErrorUri) {}

    // 게임화 / 학습 정책 상수 (EXP 보상, streak 상한, 추천 수, 통계 윈도우, 통과 점수 등).
    // passThreshold 는 step 통과 여부를 판정하는 0~100 점수 임계. 기본 80.
    // priorAttemptsCap 은 LLM 호출에 묶어 보내는 이전 시도 최대 개수 (가장 최근부터). 토큰 폭증 방지.
    public record Gamification(
            int completionExp,
            int streakCap,
            int dailyRecommended,
            int weeklyTopN,
            int weeklyWindowDays,
            double scoreFallbackOnError,
            double passThreshold,
            int priorAttemptsCap,
            String defaultRankingUnitTitle,
            String defaultPracticeWord
    ) {}

    // LLM 실패 시 사용자에게 노출되는 폴백 문구 및 공통 유저 메시지.
    public record Messages(
            String recordingGuidanceFallback,
            String feedbackGuidanceFallback,
            String retryGuidanceFallback,
            String uploadTooLarge,
            String ttsTextRequired
    ) {}
}

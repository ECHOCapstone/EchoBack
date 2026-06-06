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
        Gamification gamification,
        Messages messages,
        OAuth2 oauth2,
        Admin admin,
        Auth auth,
        Legal legal
) {

    // 회원가입 시 강제되는 비밀번호 정책. 백엔드 validator 와 프론트 안내가 같은 출처를 공유한다.
    //   minLength / maxLength    : 글자수 범위.
    //   requireCategories        : lowercase / uppercase / digit / symbol 중 충족해야 하는 카테고리 개수.
    public record Auth(PasswordPolicy passwordPolicy) {
        public record PasswordPolicy(int minLength, int maxLength, int requireCategories) {}
    }

    // 약관/법적 고지 본문 버전. 본문 파일은 classpath:content/terms/{kind}-{version}.md 로 둔다.
    public record Legal(String termsVersion) {}

    // 부팅 시 적용되는 관리자 셋업.
    //   bootstrapUsername : 이미 가입된 그 username 계정을 ROLE_ADMIN 으로 승격한다 (멱등).
    //   seed              : 시연/평가용 시드 관리자 계정. 부팅 시 username 으로 DB 를 찾고
    //                       없으면 BCrypt 해시 비밀번호와 함께 ROLE_ADMIN 으로 새로 만든다.
    //                       모든 필드가 채워져 있어야 활성화되며, 어느 하나라도 비면 건너뛴다.
    public record Admin(String bootstrapUsername, SeedAdmin seed) {
        public record SeedAdmin(String username, String email, String password, String nickname) {}
    }

    public record Jwt(String secret, long expirationMs) {}

    public record Cors(
            List<String> allowedOrigins,
            // 와일드카드 호스트 패턴. allowedOrigins 에는 와일드카드를 둘 수 없으므로 별도 필드로 받는다.
            // 예: "https://*.trycloudflare.com"
            List<String> allowedOriginPatterns,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAgeSec
    ) {
        public List<String> safeAllowedOriginPatterns() {
            return allowedOriginPatterns == null ? List.of() : allowedOriginPatterns;
        }
    }

    public record ModelServer(String baseUrl, long timeoutMs) {}

    // provider 식별자 ("rule-based" | "gemini") 와 provider 별 자격증명 / 엔드포인트.
    // gemini.apiKey 는 환경 변수 (GEMINI_API_KEY) 로 주입한다 — yaml 평문 금지.
    public record Llm(String provider, Gemini gemini) {

        // models 는 어드민이 고를 수 있는 모델 후보 목록. model 은 그중 기본값.
        public record Gemini(
                String apiKey, String model, String baseUrl, long timeoutMs, List<String> models) {

            public List<String> safeModels() {
                return models == null ? List.of() : models;
            }
        }
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

    // 실제 OAuth2 로그인 결과에 따라 브라우저를 보낼 프론트엔드 URL 들.
    //   frontendRedirectUri: 정식 로그인 성공 시 — fragment (#token=...&expiresIn=...) 로 JWT 전달
    //   frontendErrorUri:    실패 시 — ?oauthError=<code> 쿼리 부착
    //   frontendSignupUri:   신규 사용자 가입 폼으로 안내할 때 — fragment 에 pendingToken + email + nicknameHint + provider 부착
    public record OAuth2(
            String frontendRedirectUri,
            String frontendErrorUri,
            String frontendSignupUri
    ) {}

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

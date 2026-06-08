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
        Challenge challenge,
        Messages messages,
        OAuth2 oauth2,
        Admin admin,
        Auth auth,
        Legal legal,
        Member member,
        Scoring scoring,
        Canonical canonical
) {

    // 회원가입 시 강제되는 비밀번호 정책. 백엔드 validator 와 프론트 안내가 같은 출처를 공유한다.
    //   minLength / maxLength    : 글자수 범위.
    //   requireCategories        : lowercase / uppercase / digit / symbol 중 충족해야 하는 카테고리 개수.
    public record Auth(PasswordPolicy passwordPolicy) {
        public record PasswordPolicy(int minLength, int maxLength, int requireCategories) {}
    }

    // 약관/법적 고지 본문 버전. 본문 파일은 classpath:content/terms/{kind}-{version}.md 로 둔다.
    public record Legal(String termsVersion) {}

    // 회원 관련 정책. retention 은 탈퇴 (soft delete) 후 hard delete 까지의 grace period 일수.
    // graceDays 가 지난 deleted_at 사용자는 retention 스케줄러가 자식 데이터까지 정리한다.
    public record Member(Retention retention) {
        public record Retention(int graceDays) {}
    }

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

        // condition 은 BadgePolicy 가 해석하는 BadgeCondition enum 값
        // (COMPLETED_FEEDBACK_COUNT / USER_STREAK / TOTAL_EXP / RECORDING_COUNT / COMPLETED_TRACK_COUNT).
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

    // 게임화 / 학습 정책 상수.
    //   completionExp / streakCap   : 챕터 완료 보상 EXP, streak 상한.
    //   dailyRecommended            : 메인 화면 "오늘의 추천 학습" 개수.
    //   weeklyTopN / weeklyWindowDays : 통계 화면의 주간 윈도 (랭킹과 무관).
    //   passThreshold               : step 통과 여부 판정 (0~100). 어드민이 런타임에 조정 가능.
    //   priorAttemptsCap            : LLM 호출에 묶는 이전 시도 최대 개수 (토큰 폭증 방지).
    public record Gamification(
            int completionExp,
            int streakCap,
            int dailyRecommended,
            int weeklyTopN,
            int weeklyWindowDays,
            double passThreshold,
            int priorAttemptsCap,
            String defaultPracticeWord
    ) {}

    // "오늘의 챌린지" 정책.
    //   dailyAttemptLimit : 사용자가 활성 챌린지에 도전할 수 있는 하루 최대 횟수.
    //   topN              : 랭킹 상위 노출 인원.
    //   rankBadge         : 챌린지 종료 시점에 등수별로 발급할 배지 식별자. 빈 값이면 그 등수는 배지 없음.
    public record Challenge(
            int dailyAttemptLimit,
            int topN,
            RankBadge rankBadge
    ) {
        public record RankBadge(String goldId, String silverId, String bronzeId) {}
    }

    // LLM 실패 시 사용자에게 노출되는 폴백 문구 및 공통 유저 메시지.
    public record Messages(
            String recordingGuidanceFallback,
            String feedbackGuidanceFallback,
            String retryGuidanceFallback,
            String uploadTooLarge,
            String ttsTextRequired
    ) {}

    // 결정적 채점 정책. ScoringService 가 이 값을 SSOT 로 본다.
    //   weakPhonemes        : 한국인 학습자 약점 음소 (SUBSTITUTION / DELETION 마다 weakPenalty 차감).
    //   weakPenalty         : 약점 음소 오류 1개당 -점.
    //   insertionPenalty    : INSERTION 1개당 -점.
    //   regularPenalty      : 약점 외 음소 SUBSTITUTION / DELETION 1개당 추가 -점 (베이스에 이미 반영되므로 보통 0).
    public record Scoring(
            List<String> weakPhonemes,
            int weakPenalty,
            int insertionPenalty,
            int regularPenalty
    ) {
        public List<String> safeWeakPhonemes() {
            return weakPhonemes == null ? List.of() : weakPhonemes;
        }
    }

    // canonical 사전 생성 정책.
    //   bootstrapOnStartup : 부팅 시 콘텐츠 테이블의 누락된 canonical 을 비동기로 채울지 여부.
    //   bootstrapPageSize  : 한 페이지 처리 건수.
    public record Canonical(boolean bootstrapOnStartup, int bootstrapPageSize) {}
}

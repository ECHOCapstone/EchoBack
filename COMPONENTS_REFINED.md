# Echo Backend Component 설계서

## 0. 메타

- **대상:** 본 프로젝트 backend 의 모든 신규 Spring Boot 컴포넌트(Controller, Service, Repository, Entity, DTO, Support, Cross-cutting, External adapter).
- **입력 문서 (자체완결 cross-ref 출처):**
  - [`API_SPEC_REFINED.md`](./API_SPEC_REFINED.md) — REST API 26개의 요청/응답/에러 contract.
  - [`ENTITIES_REFINED.md`](./ENTITIES_REFINED.md) — JPA 엔티티 10개의 스키마 + 정합성 invariants.
  - [`MODEL_SERVER_API_SPEC.md`](./MODEL_SERVER_API_SPEC.md) — 외부 모델 서버(`/analyze`, `/g2p`) 호출 contract.
- **런타임:** Spring Boot 4.0.5, Java 25.
- **루트 패키지:** `com.capstoneecho.echo_back`.
- **작성일:** 2026-05-10.
- **깊이 정책:** 본문은 **레이어 + 컴포넌트 책임 + 핵심 메서드 시그니처**까지만 적는다. 알고리즘/SQL 본문은 §부록 D 의 한 줄 정책 인덱스로만 표기. 시그니처 본문 내부의 동작 세부는 구현 시점에 별도 정책 문서로 분리한다.
- **인터페이스 분리 정책:** Service 는 기본적으로 인터페이스 없이 단일 구현체. 인터페이스 분리는 **구현체 swap 가능성이 실제로 있는 컴포넌트**에 한정한다 (§1.2 참고).

---

## 1. 개요

### 1.1 레이어 정의

| 레이어 | 책임 |
| --- | --- |
| Controller | HTTP 진입점. 요청 DTO 검증 위임, 인증 컨텍스트 추출(`@CurrentUser`), service 호출, 응답 DTO 를 `ApiResponse<T>` 로 wrap. 비즈니스 로직 금지. |
| Service | 도메인 흐름 및 트랜잭션 경계. user-scope 리포지토리 메서드로 엔티티 참조를 얻고 entity 정적 팩토리/도메인 메서드를 호출. 외부 어댑터 호출. ErrorCode → `BusinessException` 변환. |
| Repository | Spring Data JPA 인터페이스. user-scope 메서드만 노출(`findByIdAndUser_Id`, `findByUser_Id*`). 글로벌 `findById` 노출 금지(ENTITIES_REFINED §5.1). |
| Domain entity | ENTITIES_REFINED §2 의 10개 엔티티. invariant 강제 책임은 정적 팩토리와 DB CHECK 제약. |
| Support | 도메인 보조 컴포넌트(splitter, scoring, mapper, prompt builder, storage adapter, model client) — 같은 서브도메인의 `support/` 하위에 둔다. |
| Cross-cutting | `global/` 하위. envelope, error 매핑, security, jwt, config, multipart 설정. |
| External adapter | 도메인 횡단으로 사용되는 외부 시스템 클라이언트(`external/` 하위). |

### 1.2 인터페이스 분리 정책

도메인 서비스(`AuthService`, `MemberService`, `ScriptService`, `TrackService`, `SessionService`, `RecordingService`, `FeedbackService`, `TtsService`, `RankingService`, `StatsService`)는 **모두 concrete class 단일 구현**. 인터페이스 분리는 다음 두 부류에만 적용한다.

| 컴포넌트 | 인터페이스 | 이유 |
| --- | --- | --- |
| `LlmClient` | ✅ | rule-based 기본 구현(`RuleBasedLlmFeedbackGenerator`) ↔ Gemini 구현(`GeminiLlmFeedbackGenerator`) provider profile swap. |
| `RecordingStorage` | ✅ | 로컬 디스크(`LocalRecordingStorage`) → 추후 S3/GCS swap 예정. |
| `TtsClient` | ✅ | TTS provider 교체 가능성을 열어둠. 초기 구현체 1개. |
| `ModelServerClient` | ❌ | `RestClientModelServerClient` 단일 구현. 현재 swap 계획 없음(테스트는 mockMvc/wiremock 으로 대체). 필요 시 추후 인터페이스 추출. |
| `ScoringPolicy` 등 정책류 | ❌ | 정책 변경은 클래스 본문 수정으로 처리. 다중 구현 필요해질 때만 인터페이스로 승격. |

### 1.3 도메인 그룹

API_SPEC_REFINED §3 의 도메인 분할과 1:1 매칭한다.

| 도메인 그룹 | 서브도메인 | 엔드포인트 수 |
| --- | --- | --- |
| Member | (단일) | 7 |
| Learning | track / script / session | 9 |
| Pronunciation | recording / feedback / tts | 7 |
| Statistics | stats / ranking | 2 |
| System | (단일) | 1 |

### 1.4 패키지 배치 (도메인 → 서브도메인 → 레이어 → 코드)

레이어를 최상위로 두는 horizontal 패키지가 아니라, **도메인이 최상위**, 서브도메인 → 레이어 → 코드로 내려가는 vertical 구조를 채택한다. 같은 feature 의 모든 코드가 한 폴더 트리에 모이므로 도메인 단위 탐색·삭제·이동이 쉽다.

```
com.capstoneecho.echo_back
├── global/
│   ├── common/                    # ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── security/                  # SecurityConfig, JwtAuthFilter, JwtAuthEntryPoint
│   ├── jwt/                       # JwtProvider, JwtPrincipal, CurrentUserArgumentResolver, @CurrentUser
│   └── config/                    # AppProperties, HttpClientConfig, WebMvcConfig
│
├── member/
│   ├── controller/                # AuthController, MemberController
│   ├── service/                   # AuthService, MemberService
│   ├── repository/                # UserRepository
│   ├── entity/                    # User
│   └── dto/                       # SignupRequest, LoginRequest, CheckRequest, CheckResponse,
│                                  #   UpdateNicknameRequest, TokenResponse, UserResponse
│
├── learning/
│   ├── track/
│   │   ├── controller/            # TrackController
│   │   ├── service/               # TrackService
│   │   ├── repository/            # TrackRepository
│   │   ├── entity/                # Track
│   │   └── dto/                   # TrackSummaryResponse, TrackDetailResponse, ChapterSummaryResponse
│   ├── script/
│   │   ├── controller/            # ScriptController
│   │   ├── service/               # ScriptService
│   │   ├── repository/            # ScriptRepository, LearningStepRepository
│   │   ├── entity/                # Script, LearningStep, Difficulty (enum), StepKind (enum)
│   │   ├── dto/                   # ScriptSummaryResponse, ScriptDetailResponse, StepResponse
│   │   └── support/               # RecommendedScriptSelector
│   └── session/
│       ├── controller/            # SessionController
│       ├── service/               # SessionService
│       ├── repository/            # SessionRepository, SessionSentenceRepository
│       ├── entity/                # Session, SessionSentence
│       ├── dto/                   # SessionCreateRequest, SessionUpdateRequest,
│       │                          #   SessionResponse, SessionSentenceResponse
│       └── support/               # SentenceSplitter
│
├── pronunciation/
│   ├── recording/
│   │   ├── controller/            # RecordingController
│   │   ├── service/               # RecordingService
│   │   ├── repository/            # RecordingRepository
│   │   ├── entity/                # Recording (AnalysisOutcome record 포함)
│   │   ├── dto/                   # RecordingResponse
│   │   └── support/               # RecordingStorage(인터페이스), LocalRecordingStorage,
│   │                              #   MultipartAudioReader
│   ├── feedback/
│   │   ├── controller/            # FeedbackController, FeedbacksReadController
│   │   ├── service/               # FeedbackService
│   │   ├── repository/            # FeedbackRepository, PhonemeErrorRepository
│   │   ├── entity/                # PronunciationFeedback, PhonemeError
│   │   ├── dto/                   # GenerateFeedbackRequest, FeedbackResponse,
│   │   │                          #   FeedbackSummaryResponse, RetryWordResponse,
│   │   │                          #   PhonemeErrorResponse, WrongWord
│   │   └── support/               # ScoringPolicy, PracticeWordResolver, WeakPhonemeAnalyzer,
│   │                              #   PronunciationPromptBuilder, PromptTemplates, PhonemeErrorMapper
│   └── tts/
│       ├── controller/            # TtsController
│       ├── service/               # TtsService
│       ├── dto/                   # TtsRequest
│       └── support/               # TtsClient(인터페이스), DefaultTtsClient
│
├── statistics/
│   ├── stats/
│   │   ├── controller/            # StatsController
│   │   ├── service/               # StatsService
│   │   ├── dto/                   # StatsResponse, Attendance, PhonemeFrequency, Badge
│   │   └── support/               # BadgePolicy
│   └── ranking/
│       ├── controller/            # RankingController
│       ├── service/               # RankingService
│       ├── repository/            # DemoRankingEntryRepository
│       ├── entity/                # DemoRankingEntry
│       └── dto/                   # RankingResponse, Entry
│
├── system/
│   └── controller/                # HealthController
│
└── external/
    ├── modelserver/               # ModelServerClient (단일 concrete class),
    │                              #   ModelAnalyzeResponse, ModelG2pResponse
    └── llm/                       # LlmClient(인터페이스),
                                   #   RuleBasedLlmFeedbackGenerator,
                                   #   GeminiLlmFeedbackGenerator, LlmResponse
```

규칙:

- **도메인이 최상위**, 서브도메인이 있으면 한 단계 더, 그 아래는 항상 `controller/` `service/` `repository/` `entity/` `dto/` 5개 고정. 해당 도메인이 그 레이어를 안 쓰면 폴더 자체를 만들지 않는다.
- 도메인 보조 컴포넌트는 같은 서브도메인의 `support/` 안. `service/` 폴더에는 service 클래스만.
- Cross-cutting 은 모두 `global/` 아래.
- 외부 시스템 어댑터는 도메인 여러 곳에서 호출되면 `external/<provider>/` 아래. 한 도메인에만 묶이면(`RecordingStorage`, `TtsClient`) 그 도메인의 `support/` 에 둔다.
- DTO 는 같은 서브도메인의 `dto/` 안. cross-domain 재사용 응답 객체는 두지 않는다(필요 시 도메인 안에서 자체 DTO 새로 정의).
- 엔티티는 자기 도메인의 `entity/` 안. cross-domain `@ManyToOne` 참조는 import 만 하고 위치 이동 금지.

---

## 2. Cross-cutting 컴포넌트

각 컴포넌트마다 ① 책임 한 줄 ② 핵심 시그니처 ③ 어떤 REFINED 절을 만족하는지를 명시한다.

### 2.1 `global.common.ApiResponse<T>`

- **책임:** 모든 REST 응답(TTS 바이너리 제외)의 envelope. `@JsonInclude(NON_NULL)` 로 null 필드 직렬화 제외.
- **시그니처:**
  ```java
  public record ApiResponse<T>(boolean success, T data, ErrorPayload error) {
      public static <T> ApiResponse<T> success(T data);
      public static ApiResponse<Void> success();
      public static ApiResponse<Void> failure(ErrorCode code, String message);
      public record ErrorPayload(String code, String message) {}
  }
  ```
- **만족하는 절:** API_SPEC_REFINED §1.1, §1.2.

### 2.2 `global.common.ErrorCode`

- **책임:** 모든 도메인 에러의 단일 source of truth. HTTP status + 기본 메시지를 함께 보유.
- **시그니처:**
  ```java
  public enum ErrorCode {
      INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
      VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "필수 값이 누락되었거나 형식이 올바르지 않습니다."),
      UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
      INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
      LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
      USERNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
      EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
      USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
      TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 트랙을 찾을 수 없습니다."),
      SCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다."),
      STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 단계를 찾을 수 없습니다."),
      SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
      SESSION_SENTENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "세션의 학습 문장을 찾을 수 없습니다."),
      RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "녹음을 찾을 수 없습니다."),
      FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "피드백을 찾을 수 없습니다."),
      AUDIO_DECODE_FAILED(HttpStatus.BAD_REQUEST, "오디오 파일을 처리할 수 없습니다."),
      MODEL_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "모델 서버에 연결할 수 없습니다."),
      MODEL_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "모델 서버 처리 중 오류가 발생했습니다."),
      INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예기치 않은 오류가 발생했습니다.");
      public HttpStatus status();
      public String defaultMessage();
  }
  ```
- **만족하는 절:** API_SPEC_REFINED 부록 A 19개 코드 전체.

### 2.3 `global.common.BusinessException`

- **책임:** 도메인 코드가 throw 하는 단일 unchecked 예외. ErrorCode 와 보강 메시지(optional)를 들고 다닌다.
- **시그니처:**
  ```java
  public class BusinessException extends RuntimeException {
      public BusinessException(ErrorCode code);
      public BusinessException(ErrorCode code, String detail);
      public BusinessException(ErrorCode code, String detail, Throwable cause);
      public ErrorCode errorCode();
  }
  ```

### 2.4 `global.common.GlobalExceptionHandler`

- **책임:** Spring `@RestControllerAdvice`. 모든 예외를 `ApiResponse.failure(...)` 로 변환하고 ErrorCode 의 status 로 응답.
- **시그니처(handler 메서드):**
  ```java
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e);
  // → VALIDATION_FAILED, 첫 필드 에러 메시지 보강

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e);
  // → INVALID_REQUEST, "업로드 파일 크기가 제한을 초과했습니다."

  @ExceptionHandler({MissingServletRequestPartException.class,
                     MissingServletRequestParameterException.class})
  ResponseEntity<ApiResponse<Void>> handleMissing(Exception e);
  // → INVALID_REQUEST

  @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
  ResponseEntity<ApiResponse<Void>> handleAuth(Exception e);
  // → UNAUTHORIZED

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> handleAny(Exception e);
  // → INTERNAL_ERROR, stack trace 서버 로그
  ```
- **만족하는 절:** API_SPEC_REFINED 부록 A 끝부분의 6종 매핑 규칙.

### 2.5 `global.security.SecurityConfig`

- **책임:** `SecurityFilterChain` 정의. STATELESS, CSRF disable, CORS 설정, 공개/보호 경로 매트릭스.
- **시그니처:**
  ```java
  @Bean SecurityFilterChain filterChain(HttpSecurity http,
                                        JwtAuthFilter jwtFilter,
                                        JwtAuthEntryPoint entryPoint,
                                        CorsConfigurationSource corsSource);

  @Bean CorsConfigurationSource corsConfigurationSource(AppProperties props);

  @Bean PasswordEncoder passwordEncoder();   // BCryptPasswordEncoder
  ```
- **공개/보호 경로 매트릭스:** `/api/auth/**`, `/api/health`, `/error`, `/actuator/health` 공개. 그 외 `/api/**` JWT 필수. 그 외 모든 경로 공개.
- **만족하는 절:** API_SPEC_REFINED §2, §2.1.

### 2.6 `global.security.JwtAuthFilter`

- **책임:** `OncePerRequestFilter`. `Authorization: Bearer <token>` 추출 → `JwtProvider.parse` → `JwtPrincipal` 을 `SecurityContextHolder` 에 설정. 토큰 없거나 invalid 면 chain 만 통과(다음 단계의 EntryPoint 가 401 처리).
- **시그니처:**
  ```java
  @Override protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain);
  ```

### 2.7 `global.security.JwtAuthEntryPoint`

- **책임:** Spring Security 의 `AuthenticationEntryPoint`. 미인증 요청에 `ApiResponse.failure(UNAUTHORIZED)` JSON + 401 응답.

### 2.8 `global.jwt.JwtProvider`

- **책임:** JWT 발급/파싱. HMAC 서명. `User` → JWT, JWT → `JwtPrincipal`. 만료/서명 위반 시 `BusinessException(INVALID_TOKEN)` throw.
- **시그니처:**
  ```java
  public class JwtProvider {
      public IssuedToken issue(User user);
      public JwtPrincipal parse(String token);
      public record IssuedToken(String accessToken, long expiresInSec) {}
  }
  ```

### 2.9 `global.jwt.JwtPrincipal`

- **책임:** 인증된 사용자의 가벼운 식별자. `SecurityContextHolder` 의 principal.
- **시그니처:**
  ```java
  public record JwtPrincipal(Long userId, String username) {}
  ```

### 2.10 `global.jwt.CurrentUserArgumentResolver` + `@CurrentUser`

- **책임:** 컨트롤러 메서드 파라미터 `@CurrentUser Long userId` 또는 `@CurrentUser JwtPrincipal principal` 자동 주입.
- **시그니처:**
  ```java
  @Target(ElementType.PARAMETER) @Retention(RUNTIME) public @interface CurrentUser {}

  @Component public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
      @Override public boolean supportsParameter(MethodParameter p);
      @Override public Object resolveArgument(MethodParameter p,
                                              ModelAndViewContainer mav,
                                              NativeWebRequest req,
                                              WebDataBinderFactory binder);
  }
  ```

### 2.11 `global.config.AppProperties`

- **책임:** `@ConfigurationProperties(prefix = "app")` 단일 프로퍼티 binder.
- **시그니처:**
  ```java
  @ConfigurationProperties(prefix = "app")
  public record AppProperties(
      Cors cors,
      Jwt jwt,
      ModelServer modelServer,
      Feedback feedback,
      Storage storage,
      Llm llm,
      Tts tts
  ) {
      public record Cors(List<String> allowedOrigins) {}
      public record Jwt(String secret, long accessTokenTtlSec) {}
      public record ModelServer(String baseUrl, long timeoutMs) {}
      public record Feedback(String defaultPracticeWord, int completionExp) {}
      public record Storage(String localRoot) {}
      public record Llm(String provider) {}
      public record Tts(String provider) {}
  }
  ```

### 2.12 `global.config.HttpClientConfig`

- **책임:** 외부 HTTP 호출용 `RestClient` 빈 정의.
- **시그니처:**
  ```java
  @Bean RestClient modelRestClient(AppProperties props);
  ```
- **구성 요점(MODEL_SERVER_API_SPEC §2.2):** HTTP/1.1 강제, JDK `HttpClient` + `JdkClientHttpRequestFactory`, connect/read timeout 모두 `timeoutMs`, retry 없음, 기본 message converter.

### 2.13 `global.config.WebMvcConfig`

- **책임:** `WebMvcConfigurer`. argument resolver 등록, CORS, multipart 한도(`spring.servlet.multipart.max-file-size: 25MB`, `max-request-size: 25MB`) 정합성 체크.
- **시그니처:**
  ```java
  @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers);
  @Override public void addCorsMappings(CorsRegistry registry);
  ```

---

## 3. 도메인 컴포넌트

각 도메인 절은 다음 5개 하위 절 고정 형식이다.

1. **Controllers** — base path, 메서드별 (HTTP, 경로, 인증, 요청 DTO, 응답 DTO, 호출하는 service 메서드, API_SPEC_REFINED §X.Y 인용).
2. **Services** — 단일 concrete class 의 public 메서드 시그니처. (트랜잭션 경계 readOnly true/false, 의존하는 repo 메서드, 호출하는 entity 정적 팩토리/도메인 메서드, 외부 client, 발생시키는 ErrorCode 목록).
3. **Repositories** — Spring Data JPA 메서드 시그니처. user-scope 강제(ENTITIES_REFINED §5.1)가 적용되는 메서드는 그 이유 한 줄.
4. **Entities** — ENTITIES_REFINED §2 의 어느 엔티티를 사용/소유하는지 cross-ref.
5. **Support / DTO 매핑** — 매퍼 위치, 도메인 보조 클래스.

### 3.1 Member

#### 3.1.1 Controllers

`member.controller.AuthController` (base `/api/auth`, 모두 공개)

| 메서드 | HTTP | 경로 | 요청 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- | --- |
| `signup` | POST | `/signup` | `SignupRequest` | `ApiResponse<TokenResponse>` (201) | `AuthService.signup` | §4.2 |
| `login` | POST | `/login` | `LoginRequest` | `ApiResponse<TokenResponse>` | `AuthService.login` | §4.2 |
| `checkUsername` | POST | `/check-username` | `CheckRequest` | `ApiResponse<CheckResponse>` | `AuthService.isUsernameAvailable` | §4.2 |
| `checkEmail` | POST | `/check-email` | `CheckRequest` | `ApiResponse<CheckResponse>` | `AuthService.isEmailAvailable` | §4.2 |
| `googleDemo` | GET | `/oauth2/google/demo` | — | `ApiResponse<TokenResponse>` | `AuthService.loginGoogleDemo` | §4.2 |

`member.controller.MemberController` (base `/api/members`, JWT 필수)

| 메서드 | HTTP | 경로 | 요청 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- | --- |
| `me` | GET | `/me` | — | `ApiResponse<UserResponse>` | `MemberService.getMyProfile(userId)` | §4.3 |
| `updateNickname` | PATCH | `/me/nickname` | `UpdateNicknameRequest` | `ApiResponse<UserResponse>` | `MemberService.changeNickname(userId, nickname)` | §4.3 |

#### 3.1.2 Services

`member.service.AuthService`

```java
@Service @Transactional
public class AuthService {
    TokenResponse signup(SignupRequest req);
    TokenResponse login(LoginRequest req);
    @Transactional(readOnly = true) CheckResponse isUsernameAvailable(String value);
    @Transactional(readOnly = true) CheckResponse isEmailAvailable(String value);
    TokenResponse loginGoogleDemo();
}
```

- 의존: `UserRepository`, `PasswordEncoder`, `JwtProvider`, `MemberService`(데모 사용자 upsert 시).
- 호출하는 entity 정적 팩토리: `User.create(username, email, passwordHash, nickname)`.
- ErrorCode: `VALIDATION_FAILED`(자동), `USERNAME_DUPLICATED`, `EMAIL_DUPLICATED`, `LOGIN_FAILED`.
- Google demo: 정해진 `(username, email, nickname)` 으로 `findByEmail` → 없으면 `User.create` + save → `JwtProvider.issue`. 별도 OAuth2 client 호출 없음.

`member.service.MemberService`

```java
@Service @Transactional
public class MemberService {
    @Transactional(readOnly = true) UserResponse getMyProfile(Long userId);
    UserResponse changeNickname(Long userId, String nickname);
    User awardCompletionRewards(Long userId, int expReward);   // FeedbackService 가 호출
    User loadUser(Long userId);                                 // 다른 service 가 reference 획득용
}
```

- 의존: `UserRepository`.
- 호출하는 entity 도메인 메서드: `User.changeNickname(...)`, `User.recordCompletion(expReward, ZoneId)`.
- ErrorCode: `USER_NOT_FOUND`, `VALIDATION_FAILED`.
- `awardCompletionRewards` 는 FeedbackService 가 atomic UPDATE 성공 후에만 호출(§5).

#### 3.1.3 Repositories

`member.repository.UserRepository`

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

- `User` 자체가 user-scope 의 root 이므로 글로벌 `findById` 는 `MemberService.loadUser` 내부에서만 사용.

#### 3.1.4 Entities

| 엔티티 | ENTITIES_REFINED |
| --- | --- |
| `User` | §2.1 (소유) |

#### 3.1.5 Support / DTO 매핑

- DTO → entity 변환은 `AuthService` 본문에서 `User.create(...)` 호출.
- entity → DTO 변환은 `UserResponse.from(User)` static factory.
- `TokenResponse.of(JwtProvider.IssuedToken, UserResponse)` static factory.

---

### 3.2 Learning

#### 3.2.1 Track sub-domain

##### Controllers

`learning.track.controller.TrackController` (base `/api/tracks`, JWT 필수)

| 메서드 | HTTP | 경로 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- |
| `list` | GET | `` | `ApiResponse<List<TrackSummaryResponse>>` | `TrackService.listAll()` | §4.9 |
| `detail` | GET | `/{trackId}` | `ApiResponse<TrackDetailResponse>` | `TrackService.getDetail(trackId)` | §4.9 |

##### Services

`learning.track.service.TrackService`

```java
@Service @Transactional(readOnly = true)
public class TrackService {
    List<TrackSummaryResponse> listAll();
    TrackDetailResponse getDetail(Long trackId);
}
```

- 의존: `TrackRepository`, `ScriptRepository`(detail 시 챕터 목록 조회).
- ErrorCode: `TRACK_NOT_FOUND`.

##### Repositories

`learning.track.repository.TrackRepository`

```java
public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findAllByOrderByDisplayOrderAsc();
}
```

- 트랙은 사용자 소유가 아닌 전역 콘텐츠라 user-scope 메서드 없음.

##### Entities / Support

| 엔티티 | ENTITIES_REFINED |
| --- | --- |
| `Track` | §2.2 (소유) |

- `TrackSummaryResponse.from(Track, int chapterCount)`, `TrackDetailResponse.of(Track, List<ChapterSummaryResponse>)`, `ChapterSummaryResponse.from(Script)`.

#### 3.2.2 Script sub-domain

##### Controllers

`learning.script.controller.ScriptController` (base `/api/scripts`, JWT 필수)

| 메서드 | HTTP | 경로 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- |
| `recommendedToday` | GET | `/recommended/today` | `ApiResponse<List<ScriptSummaryResponse>>` | `ScriptService.recommendedToday()` | §4.8 |
| `detail` | GET | `/{scriptId}` | `ApiResponse<ScriptDetailResponse>` | `ScriptService.getDetail(scriptId)` | §4.8 |

##### Services

`learning.script.service.ScriptService`

```java
@Service @Transactional(readOnly = true)
public class ScriptService {
    List<ScriptSummaryResponse> recommendedToday();
    ScriptDetailResponse getDetail(Long scriptId);
    Script loadScript(Long scriptId);                   // 다른 service 가 reference 획득용
    LearningStep loadStep(Long stepId);
}
```

- 의존: `ScriptRepository`, `LearningStepRepository`, `RecommendedScriptSelector`.
- 호출하는 entity 정적 팩토리: 없음(읽기 전용).
- ErrorCode: `SCRIPT_NOT_FOUND`, `STEP_NOT_FOUND`.
- `recommendedToday` 정책은 §부록 D.RecommendedScriptSelector 한 줄.

##### Repositories

`learning.script.repository.ScriptRepository`

```java
public interface ScriptRepository extends JpaRepository<Script, Long> {
    List<Script> findByTrack_IdOrderByChapterOrderAsc(Long trackId);
    List<Script> findByPresetTrueAndDifficulty(Difficulty difficulty);
    List<Script> findByPresetTrue();
}
```

`learning.script.repository.LearningStepRepository`

```java
public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {
    List<LearningStep> findByScript_IdOrderByOrderIndexAsc(Long scriptId);
}
```

- 둘 다 전역 콘텐츠 — user-scope 메서드 없음.

##### Entities / Support

| 엔티티 | ENTITIES_REFINED |
| --- | --- |
| `Script` | §2.3 (소유) |
| `LearningStep` | §2.4 (소유) |
| `Difficulty` (enum) | 부록 A |
| `StepKind` (enum) | 부록 A |

- `ScriptSummaryResponse.from(Script)`, `ScriptDetailResponse.of(Script, List<StepResponse>)`, `StepResponse.from(LearningStep)`(INTRO 단계는 `targetText` null → `@JsonInclude(NON_NULL)` 으로 omit).
- `learning.script.support.RecommendedScriptSelector` — 오늘의 추천 스크립트 정책. 시그니처: `List<Script> select(LocalDate today, Long userId)`. 본문 정책은 §부록 D.

#### 3.2.3 Session sub-domain

##### Controllers

`learning.session.controller.SessionController` (base `/api/sessions`, JWT 필수)

| 메서드 | HTTP | 경로 | 요청 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- | --- |
| `list` | GET | `` | — | `ApiResponse<List<SessionResponse>>` | `SessionService.listMine(userId)` | §4.7 |
| `create` | POST | `` | `SessionCreateRequest` | `ApiResponse<SessionResponse>` (201) | `SessionService.create(userId, req)` | §4.7 |
| `detail` | GET | `/{sessionId}` | — | `ApiResponse<SessionResponse>` | `SessionService.getMine(userId, sessionId)` | §4.7 |
| `patch` | PATCH | `/{sessionId}` | `SessionUpdateRequest` | `ApiResponse<SessionResponse>` | `SessionService.patch(userId, sessionId, req)` | §4.7 |
| `delete` | DELETE | `/{sessionId}` | — | `ApiResponse<Void>` | `SessionService.delete(userId, sessionId)` | §4.7 |

##### Services

`learning.session.service.SessionService`

```java
@Service @Transactional
public class SessionService {
    @Transactional(readOnly = true) List<SessionResponse> listMine(Long userId);
    @Transactional(readOnly = true) SessionResponse getMine(Long userId, Long sessionId);
    SessionResponse create(Long userId, SessionCreateRequest req);
    SessionResponse patch(Long userId, Long sessionId, SessionUpdateRequest req);
    void delete(Long userId, Long sessionId);
    Session loadOwnedSession(Long userId, Long sessionId);              // 다른 service 가 reference 획득용
    SessionSentence loadOwnedSentence(Long userId, Long sentenceId);    // 동일
}
```

- 의존: `SessionRepository`, `SessionSentenceRepository`, `SentenceSplitter`, `MemberService.loadUser`(생성 시).
- 호출하는 entity 정적 팩토리: `Session.create(User, title)`, `SessionSentence.of(session, idx, text)`.
- 호출하는 entity 도메인 메서드: `Session.rename`, `Session.setFavorite`, `Session.updateScript(scriptText, sentenceTexts)`.
- 두 단계 검증(ENTITIES_REFINED §5.1): repo 의 `findByIdAndUser_Id` 로 cross-user 차단 → entity 정적 팩토리/도메인 메서드 호출.
- ErrorCode: `SESSION_NOT_FOUND`, `SESSION_SENTENCE_NOT_FOUND`, `VALIDATION_FAILED`.
- `delete` 는 `sessions` 행 hard delete. Recording / PronunciationFeedback 의 `session_id` 는 `ON DELETE SET NULL` 로 끊어지고 본문 보존(ENTITIES_REFINED §2.7, §2.8). session-flow recording / feedback 은 cascade 결과 `script_id` / `session_id` 가 모두 NULL 인 history 행이 되며, 이는 ENTITIES_REFINED §2.7 / §2.8 의 완화된 CHECK 식 (`양쪽 NULL OR XOR`) 을 통과한다. INSERT 시점의 strict XOR 은 Recording / PronunciationFeedback 정적 팩토리가 application-level 로 보장.

##### Repositories

`learning.session.repository.SessionRepository`

```java
public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByIdAndUser_Id(Long id, Long userId);
    List<Session> findByUser_IdOrderByFavoriteDescUpdatedAtDesc(Long userId);
}
```

`learning.session.repository.SessionSentenceRepository`

```java
public interface SessionSentenceRepository extends JpaRepository<SessionSentence, Long> {
    Optional<SessionSentence> findByIdAndSession_User_Id(Long id, Long userId);
}
```

- 두 리포지토리 모두 user-scope 메서드만 외부 노출(글로벌 `findById` 는 service 본문에서만 cross-user 검증 후 사용 가능).

##### Entities / Support

| 엔티티 | ENTITIES_REFINED |
| --- | --- |
| `Session` | §2.5 (소유) |
| `SessionSentence` | §2.6 (소유) |

- `learning.session.support.SentenceSplitter` — `List<String> split(String scriptText)`. 본문 규칙은 §부록 D.
- `SessionResponse.from(Session)`, `SessionSentenceResponse.from(SessionSentence)`.

---

### 3.3 Pronunciation

#### 3.3.1 Recording

##### Controllers

`pronunciation.recording.controller.RecordingController` (base `/api/recordings`, JWT 필수)

| 메서드 | HTTP | 경로 | 요청 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- | --- |
| `upload` | POST | `` | `multipart/form-data` part `audio` + query `scriptId?`, `sessionId?`, `stepId?`, `sessionSentenceId?` | `ApiResponse<RecordingResponse>` (201) | `RecordingService.upload(userId, audio, ctx)` | §4.6 |

##### Services

`pronunciation.recording.service.RecordingService`

```java
@Service @Transactional
public class RecordingService {
    RecordingResponse upload(Long userId,
                             MultipartFile audio,
                             RecordingContext ctx);

    Recording loadOwnedRecording(Long userId, Long recordingId);
    List<Recording> loadOwnedRecordings(Long userId, Collection<Long> recordingIds);

    public record RecordingContext(Long scriptId, Long sessionId, Long stepId, Long sessionSentenceId) {}

    // 흐름 ① — 사용자 + 부모 엔티티 조회 + 컨텍스트 ID 조합 검증을 단일 helper 로 묶음.
    // 결과는 sealed interface 로 mode 별 타입 안전성 확보 → ⑩ 의 정적 팩토리 호출이 패턴 매칭으로 분기 가능.
    private enum Mode { SCRIPT_FLOW, SESSION_SENTENCE, SESSION_FREE_FORM }

    private sealed interface ResolvedParents
            permits ScriptStep, SessionSentenceParents, SessionFreeForm {
        User user();
    }
    private record ScriptStep            (User user, Script script, LearningStep step)         implements ResolvedParents {}
    private record SessionSentenceParents(User user, Session session, SessionSentence sentence) implements ResolvedParents {}
    private record SessionFreeForm       (User user, Session session)                          implements ResolvedParents {}

    private ResolvedParents resolveParents(Long userId, RecordingContext ctx);   // ① user 로드 + detectMode + user-scope 부모 조회
    private Mode            detectMode    (RecordingContext ctx);                // 4 ID set/null 패턴 → canonical 3-mode (그 외 INVALID_REQUEST)
}
```

`resolveParents` / `detectMode` 책임 경계:

| 검증 종류 | 위치 | 위반 시 ErrorCode |
| --- | --- | --- |
| 컨텍스트 ID 조합 (mode 결정) | `detectMode` | `INVALID_REQUEST` |
| 부모 엔티티 존재 + user 소유 (1단계) | `resolveParents` 의 mode 별 user-scope repo 호출 | `USER_NOT_FOUND` / `SCRIPT_NOT_FOUND` / `STEP_NOT_FOUND` / `SESSION_NOT_FOUND` / `SESSION_SENTENCE_NOT_FOUND` |
| 부모 엔티티 간 cross-parent 일관성 (2단계) | ⑤ `Recording.validateForXxx` + ⑩ 정적 팩토리 | `INTERNAL_ERROR` (1단계 누락 시의 프로그래밍 에러) |

- 흐름 (commit-time 보상을 `TransactionSynchronization.afterCompletion` 에 위임 — 본문/flush/commit 시점 실패를 단일 정책으로 정리):
  1. `parents = resolveParents(userId, ctx)` — 사용자 로드 + 컨텍스트 mode 분기 + user-scope 부모 엔티티 조회. `USER_NOT_FOUND` / `INVALID_REQUEST` / `SCRIPT_NOT_FOUND` / `STEP_NOT_FOUND` / `SESSION_NOT_FOUND` / `SESSION_SENTENCE_NOT_FOUND`.
  2. `decoded = multipartAudioReader.read(audio)` — 메모리에서 WAV 변환·duration 추출. 실패 시 `AUDIO_DECODE_FAILED`.
  3. `targetText` snapshot 결정 (mode 별 — `parents` 의 record 변종 패턴 매칭).
  4. `Recording.validateForScriptStep / validateForSessionSentence / validateForSessionFreeForm` 호출 — 정적 팩토리 invariant (ENTITIES_REFINED §2.7) 와 동일한 cross-parent 검증을 객체 생성 *전에* 한 번 더 수행. 위반 시 `IllegalArgumentException` (`INTERNAL_ERROR` 로 매핑 — 두 단계 검증 1단계가 빠진 프로그래밍 에러).
  5. `ModelServerClient.g2p(targetText)` — canonical 산출 (해당 mode 만, 빈 입력은 `""` 반환).
  6. `ModelServerClient.analyze(decoded.wavBytes(), canonical)` — perceived/peakSoftmax/errors/durationSec.
  7. `outcome = buildOutcome(targetText, analyze, parents)` — `ScoringPolicy` + `WeakPhonemeAnalyzer` + `LlmClient.summarizeRecording(...)` (`RecordingGuidance(guidanceKr, wrongWords)` 반환 — wrongWords 의 JSON 직렬화도 본 단계에서 수행해 `outcome.wrongWordsJson` 채움) + `PhonemeErrorMapper` 로 `AnalysisOutcome` 빌드.
  8. `audioPath = recordingStorage.save(userId, decoded.wavBytes(), audio.getOriginalFilename())` — 디스크 저장. 실패 시 그대로 예외 전파 (`LocalRecordingStorage` 는 write-then-rename 패턴 — partial 파일 잔존 없음).
  9. **`TransactionSynchronizationManager.registerSynchronization(...)`** — `afterCompletion(int status)` 콜백 등록. `status != STATUS_COMMITTED` 이면 `recordingStorage.delete(audioPath)` 호출 (try-catch 로 감싸 2차 실패는 WARN 로그만). 본문/flush/commit/unknown 모든 비-commit 결과를 동일 경로로 정리.
  10. `Recording.forScriptStep / forSessionSentence / forSessionFreeForm` 정적 팩토리 호출 (ENTITIES_REFINED §2.7) — 인자에 ⑧ 의 `audioPath` 포함 + ⑦ 의 `outcome` 적용 + 저장. 본문 try-catch 없음 (⑨ 의 동기화가 모든 실패 경로 책임):
      - `recording = Recording.forXxx(parents..., audioPath, targetText)`.
      - `recording.applyAnalysis(outcome)`.
      - `recordingRepository.save(recording)`.
  11. `RecordingResponse.from(recording)`.

  > ① ~ ⑦ 단계는 모두 인-메모리 또는 외부 호출이므로 모델 서버 timeout/에러나 cross-parent 검증 실패가 일어나도 storage 호출(⑧) 보다 앞이라 디스크에 orphan WAV 가 남지 않는다.

  > ⑧ 이후의 commit-time 실패 — ⑩ 본문 예외, JPA flush 시 DB constraint 위반, 트랜잭션 매니저 commit 실패 등 — 는 모두 service 본문 catch 로는 잡을 수 없다 (`@Transactional` 의 commit 은 메서드 본문 종료 후 일어남). ⑨ 의 `TransactionSynchronization.afterCompletion` 이 `status` 로 분기하여 단일 정책으로 정리한다: 정상 commit → no-op (파일 유지); rollback / unknown → `recordingStorage.delete(audioPath)`.

- 의존: `RecordingRepository`, `MultipartAudioReader`, `RecordingStorage`, `ModelServerClient`, `LlmClient`, `ScoringPolicy`, `WeakPhonemeAnalyzer`, `PhonemeErrorMapper`, `MemberService.loadUser`, `ScriptService.loadScript / loadStep`, `SessionService.loadOwnedSession / loadOwnedSentence`, `TransactionSynchronizationManager` (Spring framework, no DI).
- 발생 ErrorCode: `INVALID_REQUEST`(컨텍스트 조합 위반), `USER_NOT_FOUND`, `AUDIO_DECODE_FAILED`, `SCRIPT_NOT_FOUND`, `SESSION_NOT_FOUND`, `STEP_NOT_FOUND`, `SESSION_SENTENCE_NOT_FOUND`, `MODEL_SERVER_UNAVAILABLE`, `MODEL_SERVER_ERROR`. (모델 서버 두 코드는 ⑤/⑥ 단계에서 발생할 수 있고 storage 호출(⑧) 보다 앞이라 디스크 잔존 0. ⑧ 이후의 본문/flush/commit 시점 실패는 ⑨ TransactionSynchronization 이 일괄 정리.)

##### Repositories

`pronunciation.recording.repository.RecordingRepository`

```java
public interface RecordingRepository extends JpaRepository<Recording, Long> {
    Optional<Recording> findByIdAndUser_Id(Long id, Long userId);
    List<Recording> findByUser_IdAndIdIn(Long userId, Collection<Long> ids);

    // FeedbackService.generate 의 컨텍스트 정합 검증 — user-scope + 부모 unit 일치를 SQL 레벨에서 강제.
    // ON DELETE SET NULL 로 부모가 끊긴 recording (script_id=NULL / session_id=NULL) 은 자동 제외됨.
    List<Recording> findByUser_IdAndScript_IdAndIdIn(Long userId, Long scriptId, Collection<Long> ids);
    List<Recording> findByUser_IdAndSession_IdAndIdIn(Long userId, Long sessionId, Collection<Long> ids);
}
```

- user-scope 만 노출. cross-user ID 는 빈 결과 → service 가 `RECORDING_NOT_FOUND` 변환.
- 마지막 두 메서드는 `FeedbackService.generate` 가 mode 별로 호출. 결과 size 가 입력 ID 수와 다르면 cross-user / cross-context / 부모 끊김 모두 동일하게 `RECORDING_NOT_FOUND` 로 통합 변환 (클라이언트 분기 단순화).

##### Entities / Support

| 엔티티 | ENTITIES_REFINED |
| --- | --- |
| `Recording` (+ 내부 record `AnalysisOutcome`) | §2.7 (소유) |
| `User` / `Script` / `LearningStep` / `Session` / `SessionSentence` | 참조만 |

`Recording` 엔티티는 ENTITIES_REFINED §2.7 의 정적 팩토리 3종 (`forScriptStep`, `forSessionSentence`, `forSessionFreeForm`) 외에 본 명세서가 추가로 다음 검증 전용 정적 메서드를 요구한다 — `RecordingService.upload` 의 흐름 ⑤ 에서 `audioPath` 가 아직 결정되지 않은 시점의 cross-parent 검증을 위해 사용한다.

```java
// 객체를 생성하지 않고 invariant 만 검증. 위반 시 IllegalArgumentException.
public static void validateForScriptStep(User user, Script script, LearningStep step);
public static void validateForSessionSentence(User user, Session session, SessionSentence sentence);
public static void validateForSessionFreeForm(User user, Session session);
```

정적 팩토리 본문은 변경하지 않는다 (생성 시점에 같은 invariant 를 한 번 더 검증 — 이중 안전). 검증 로직을 위 3개 정적 메서드와 정적 팩토리가 공유하도록 구현한다.

- `pronunciation.recording.support.RecordingStorage` (인터페이스) + `LocalRecordingStorage`.
  ```java
  public interface RecordingStorage {
      String save(Long userId, byte[] wavBytes, String originalFilename);   // returns audioPath
      byte[] load(String audioPath);
      void   delete(String audioPath);                                       // 보상/관리 경로 — 멱등.
  }
  ```
  - `delete` 는 멱등 (없는 path 도 silent OK). 실패는 WARN 로그만 기록하고 예외를 throw 하지 않는다 — 보상 경로의 2차 실패가 원본 예외(`RecordingService.upload` 의 DB save 예외 등)를 가리지 않도록.
- `pronunciation.recording.support.MultipartAudioReader`
  ```java
  public class MultipartAudioReader {
      public Decoded read(MultipartFile audio);          // throws BusinessException(AUDIO_DECODE_FAILED)
      public record Decoded(byte[] wavBytes, double durationSec) {}
  }
  ```
- `RecordingResponse.from(Recording)` — context 키들은 null 이면 `@JsonInclude(NON_NULL)` 로 omit (API_SPEC_REFINED §4.6 응답 예시). `wrongWords` 는 `recording.wrongWordsJson` 을 `WrongWord[]` (API_SPEC_REFINED §5.15) 로 역직렬화 (NULL → `[]`). 역직렬화 실패는 WARN 로그 + 빈 배열 fallback (응답 contract: "없으면 `[]`").

#### 3.3.2 Feedback (쓰기)

##### Controllers

`pronunciation.feedback.controller.FeedbackController` (base `/api/feedback`, JWT 필수)

| 메서드 | HTTP | 경로 | 요청 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- | --- |
| `generate` | POST | `/generate` | `GenerateFeedbackRequest` | `ApiResponse<FeedbackResponse>` | `FeedbackService.generate(userId, req)` | §4.4 |
| `retryWord` | POST | `/{feedbackId}/retry-word` | `multipart/form-data` part `audio` | `ApiResponse<RetryWordResponse>` | `FeedbackService.retryWord(userId, feedbackId, audio)` | §4.4 |
| `complete` | POST | `/{feedbackId}/complete` | — | `ApiResponse<UserResponse>` | `FeedbackService.complete(userId, feedbackId)` | §4.4 |

##### Services

`pronunciation.feedback.service.FeedbackService`

```java
@Service @Transactional
public class FeedbackService {
    FeedbackResponse generate(Long userId, GenerateFeedbackRequest req);
    RetryWordResponse retryWord(Long userId, Long feedbackId, MultipartFile audio);
    UserResponse complete(Long userId, Long feedbackId);

    @Transactional(readOnly = true) List<FeedbackSummaryResponse> listMine(Long userId);
    @Transactional(readOnly = true) FeedbackResponse getMine(Long userId, Long feedbackId);
}
```

- `generate` 흐름:
  1. `req.scriptId` XOR `req.sessionId` 검증 — 위반 시 `INVALID_REQUEST`.
  2. user-scope 로 Script/Session 조회 (`ScriptRepository.findById` / `SessionRepository.findByIdAndUser_Id`). 빈 결과 → `SCRIPT_NOT_FOUND` / `SESSION_NOT_FOUND`.
  3. **mode 별 컨텍스트-스코프 조회** (Codex finding F1 반영):
     - script-flow: `recordingRepository.findByUser_IdAndScript_IdAndIdIn(userId, scriptId, recordingIds)`.
     - session-flow: `recordingRepository.findByUser_IdAndSession_IdAndIdIn(userId, sessionId, recordingIds)`.
     - 결과 size 가 입력 ID 수와 다르면 `RECORDING_NOT_FOUND` (cross-user / cross-context / ON DELETE SET NULL 로 부모 끊긴 케이스 모두 동일하게 404 로 통합).
  4. `ScoringPolicy.aggregate(recordings)` → `WeakPhonemeAnalyzer.topOne(...)` → `PracticeWordResolver.resolve(...)` → `LlmClient.summarizeFeedback(...)`.
  5. `PronunciationFeedback.create(user, script, session, title, accuracy, weakPhoneme, practiceWord, guidanceKr)` 정적 팩토리 (ENTITIES_REFINED §2.8).
  6. `PhonemeError` 들 `addError` → save → `FeedbackResponse.from(feedback)`.
- `retryWord` 흐름: ① user-scope 로 feedback 조회(없으면 `FEEDBACK_NOT_FOUND`) → ② `MultipartAudioReader.read` → ③ `ModelServerClient.g2p(feedback.practiceWord)` → ④ `ModelServerClient.analyze(wav, canonical)` → ⑤ `ScoringPolicy.singleWordScore(...)`, `LlmClient.retryGuidance(...)` → ⑥ `RetryWordResponse` 반환(저장 없음).
- `complete` 흐름 (Codex finding F2 반영 — zero-row 를 not-exists 와 already-completed 로 분기):
  1. `feedbackRepository.markCompletedAtomically(feedbackId, userId, Instant.now())` 호출 — `now` 는 service 가 주입 (ENTITIES_REFINED §5.2 / §5.3, F8 반영). 같은 SQL 안에서 `completed=true` 와 `completed_at=:now` 가 동시에 set 되어 보상 시점과 attendance 캘린더 일자가 정확히 일치.
  2. 영향 행 1 → `MemberService.awardCompletionRewards(userId, props.feedback().completionExp())` → 갱신된 `UserResponse`.
  3. 영향 행 0 → `feedbackRepository.findByIdAndUser_Id(feedbackId, userId)` 추가 조회로 분기:
     - present + `completed == true` → 보상 가산 없이 `MemberService.getMyProfile(userId)` 반환 (idempotent — 같은 사용자가 두 번째 호출).
     - empty → `BusinessException(FEEDBACK_NOT_FOUND)` (cross-user 또는 not-exists. API_SPEC_REFINED §4.4 의 contract 보존).
     - present + `completed == false` → 이론적으로 도달 불가 (atomic UPDATE 가 1을 반환했어야 함). 방어적으로 `BusinessException(INTERNAL_ERROR)`.

  > 본 분기 명세는 ENTITIES_REFINED §5.3 의 "0 → 가산 없이 현재 사용자 정보만 반환" 조항을 보강한다 — §5.3 자체는 cross-user 측면을 명시적으로 다루지 않으나, API_SPEC_REFINED §4.4 / §7 cross-user 검증 시나리오와의 일관을 위해 본 명세서가 zero-row 분기를 강제한다. 이 추가 조회는 read-only 이므로 atomic UPDATE 의 read-modify-write 금지 원칙에 위배되지 않는다.
- 의존: `FeedbackRepository`, `PhonemeErrorRepository`, `RecordingRepository`(read), `ScriptService.loadScript`, `SessionService.loadOwnedSession`, `MemberService`, `ModelServerClient`, `LlmClient`, `ScoringPolicy`, `PracticeWordResolver`, `WeakPhonemeAnalyzer`, `PhonemeErrorMapper`, `MultipartAudioReader`.
- 발생 ErrorCode: `INVALID_REQUEST`, `SCRIPT_NOT_FOUND`, `SESSION_NOT_FOUND`, `RECORDING_NOT_FOUND`, `FEEDBACK_NOT_FOUND`, `AUDIO_DECODE_FAILED`, `MODEL_SERVER_UNAVAILABLE`, `MODEL_SERVER_ERROR`, `VALIDATION_FAILED`.

##### Repositories

`pronunciation.feedback.repository.FeedbackRepository`

```java
public interface FeedbackRepository extends JpaRepository<PronunciationFeedback, Long> {
    Optional<PronunciationFeedback> findByIdAndUser_Id(Long id, Long userId);
    List<PronunciationFeedback> findByUser_IdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("""
        UPDATE PronunciationFeedback f
           SET f.completed   = true,
               f.completedAt = :now
         WHERE f.id = :id
           AND f.user.id = :userId
           AND f.completed = false
    """)
    int markCompletedAtomically(@Param("id") Long id,
                                @Param("userId") Long userId,
                                @Param("now") Instant now);

    // StatsService.attendance group-by — completed_at 기반 일자별 count.
    // 인덱스 (user_id, completed_at) 가 not-null 부분만 효율적으로 처리.
    @Query("""
        SELECT FUNCTION('DATE', f.completedAt) AS day, COUNT(f) AS cnt
          FROM PronunciationFeedback f
         WHERE f.user.id = :userId
           AND f.completed = true
           AND f.completedAt >= :startInclusive
           AND f.completedAt <  :endExclusive
         GROUP BY FUNCTION('DATE', f.completedAt)
    """)
    List<DayCount> findCompletedDaysInMonth(@Param("userId") Long userId,
                                            @Param("startInclusive") Instant startInclusive,
                                            @Param("endExclusive")  Instant endExclusive);

    interface DayCount { LocalDate getDay(); long getCnt(); }
}
```

`pronunciation.feedback.repository.PhonemeErrorRepository`

```java
public interface PhonemeErrorRepository extends JpaRepository<PhonemeError, Long> {}
```

- `PhonemeError` 는 cascade=ALL + orphanRemoval=true 로 부모를 통해 관리되므로 직접 사용은 거의 없음. 통계용 read 가 필요할 때만 메서드 추가.

##### Entities / Support

| 엔티티 | ENTITIES_REFINED |
| --- | --- |
| `PronunciationFeedback` | §2.8 (소유) |
| `PhonemeError` | §2.9 (소유) |

- `pronunciation.feedback.support.ScoringPolicy` — `double aggregate(List<Recording>)`, `double singleWordScore(ModelAnalyzeResponse)`. 본문은 §부록 D.
- `pronunciation.feedback.support.PracticeWordResolver` — `String resolve(Script script, String weakPhoneme, LlmClient llm, AppProperties props)`. fallback chain: 시드 챕터 단어 → LLM 추천 → 음소 매핑 → `app.feedback.default-practice-word`(API_SPEC_REFINED §5.4 의 `practiceWord` non-null 보장).
- `pronunciation.feedback.support.WeakPhonemeAnalyzer` — `String topOne(List<Recording>)`, `List<PhonemeFrequency> topN(List<Recording>, int n)`(주간 stats 와 공유 가능).
- `pronunciation.feedback.support.PronunciationPromptBuilder` + `PromptTemplates` — LLM 호출 input 빌더. yaml `prompts.yaml` 에서 템플릿 로드.
- `pronunciation.feedback.support.PhonemeErrorMapper` — `ModelAnalyzeResponse.AlignmentItem` → 도메인 `PhonemeError`/`PhonemeErrorResponse` 변환.

#### 3.3.3 Feedback (조회)

##### Controllers

`pronunciation.feedback.controller.FeedbacksReadController` (base `/api/feedbacks`, JWT 필수 — 단/복수형 분리는 의도적, API_SPEC_REFINED §4.5 인용)

| 메서드 | HTTP | 경로 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- |
| `list` | GET | `` | `ApiResponse<List<FeedbackSummaryResponse>>` | `FeedbackService.listMine(userId)` | §4.5 |
| `detail` | GET | `/{feedbackId}` | `ApiResponse<FeedbackResponse>` | `FeedbackService.getMine(userId, feedbackId)` | §4.5 |

조회용 메서드 시그니처는 §3.3.2 의 `FeedbackService` 클래스 안에 함께 정의. ErrorCode: `FEEDBACK_NOT_FOUND`. DTO 매핑: `FeedbackSummaryResponse.from(PronunciationFeedback)`(`weakPhoneme` null 이면 omit), `FeedbackResponse.from(PronunciationFeedback)`.

#### 3.3.4 TTS

##### Controllers

`pronunciation.tts.controller.TtsController` (base `/api/tts`, JWT 필수)

| 메서드 | HTTP | 경로 | 요청 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- | --- |
| `synthesize` | POST | `` | `TtsRequest` | `200 OK` `audio/mpeg` raw bytes (envelope 미적용) | `TtsService.synthesize(req)` | §4.12 |

- 에러 시에는 `ApiResponse<Void>` JSON 으로 응답(`GlobalExceptionHandler` 자연 매핑). 클라이언트는 `Content-Type` 으로 분기.

##### Services

`pronunciation.tts.service.TtsService`

```java
@Service @Transactional(readOnly = true)
public class TtsService {
    byte[] synthesize(TtsRequest req);
}
```

- 의존: `TtsClient`.
- ErrorCode: `INVALID_REQUEST`, `MODEL_SERVER_UNAVAILABLE`, `MODEL_SERVER_ERROR`(TtsClient 가 외부 모델 서버를 거치는 경우 동일 매핑 재사용).

##### Support

- `pronunciation.tts.support.TtsClient` (인터페이스)
  ```java
  public interface TtsClient {
      byte[] synthesize(String text, String lang);   // lang null → 기본 영어
  }
  ```
- `pronunciation.tts.support.DefaultTtsClient` — provider profile 으로 전환 가능. 초기 구현은 모델 서버 또는 외부 TTS 호출.

---

### 3.4 Statistics

#### 3.4.1 Stats

##### Controllers

`statistics.stats.controller.StatsController` (base `/api/stats`, JWT 필수)

| 메서드 | HTTP | 경로 | 쿼리 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- | --- |
| `me` | GET | `/me` | `year?`, `month?` | `ApiResponse<StatsResponse>` | `StatsService.getMyStats(userId, year, month)` | §4.11 |

##### Services

`statistics.stats.service.StatsService`

```java
@Service @Transactional(readOnly = true)
public class StatsService {
    StatsResponse getMyStats(Long userId, Integer year, Integer month);
}
```

- 의존: `UserRepository`, `RecordingRepository`(주간 약점 음소 집계용), `FeedbackRepository`(누적 완료 수 집계, 월간 출석), `BadgePolicy`, `WeakPhonemeAnalyzer`.
- ErrorCode: `USER_NOT_FOUND`, `INVALID_REQUEST`(year/month 범위 위반).
- 정책: streak 7 캡, 배지 임계, 주간 약점 top-N 은 §부록 D.
- **Attendance 계산 (Codex finding F8 반영)**: `feedbackRepository.findCompletedDaysInMonth(userId, monthStartUtc, monthEndUtc)` 가 `completed_at` 컬럼 기반 일자별 count 를 반환 → service 가 해당 month 의 day 시퀀스를 훑어 누적 streak 변환 (출석 없는 날은 응답 map 에서 키 omit). 타임존 환산은 service 책임 — controller 가 받은 `year`/`month` + `app.stats.zone` (기본 `Asia/Seoul`) 으로 `monthStartUtc` / `monthEndUtc` 계산. NULL `completed_at` (rev4 이전 history 행 — 마이그레이션 전) 은 자동 제외. 자세한 정책은 §부록 D.

##### Support

- `statistics.stats.support.BadgePolicy` — `List<Badge> evaluate(User user, FeedbackStats stats)`. 본문은 §부록 D.

#### 3.4.2 Ranking

##### Controllers

`statistics.ranking.controller.RankingController` (base `/api/ranking`, JWT 필수)

| 메서드 | HTTP | 경로 | 응답 | 호출 service | 인용 |
| --- | --- | --- | --- | --- | --- |
| `today` | GET | `/today` | `ApiResponse<RankingResponse>` | `RankingService.today(userId)` | §4.10 |

##### Services

`statistics.ranking.service.RankingService`

```java
@Service @Transactional(readOnly = true)
public class RankingService {
    RankingResponse today(Long userId);
}
```

- 의존: `DemoRankingEntryRepository`, `FeedbackRepository`(오늘의 학습 단위 정확도 집계), `ScriptService.loadScript` 또는 `RecommendedScriptSelector`(unitTitle 결정).
- DemoRankingEntry 와 실 사용자 누적 합산 정책은 §부록 D.

##### Repositories

`statistics.ranking.repository.DemoRankingEntryRepository`

```java
public interface DemoRankingEntryRepository extends JpaRepository<DemoRankingEntry, Long> {
    List<DemoRankingEntry> findAll();
}
```

##### Entities / Support

| 엔티티 | ENTITIES_REFINED |
| --- | --- |
| `DemoRankingEntry` | §2.10 (소유) |

---

### 3.5 System

`system.controller.HealthController` (base `/api`, 공개)

| 메서드 | HTTP | 경로 | 응답 | 인용 |
| --- | --- | --- | --- | --- |
| `health` | GET | `/health` | `ApiResponse<Map<String,Object>>` `{ status, service, timestamp }` | §4.1 |

- 어떤 service 도 호출하지 않음. 컨트롤러 내부에서 `Map.of(...)` 로 즉시 응답.

---

## 4. 외부 시스템 어댑터

### 4.1 `external.modelserver.ModelServerClient`

- **분리 정책:** 단일 concrete class (`@Component`). 인터페이스 분리 없음(§1.2).
- **시그니처:**
  ```java
  @Component
  public class ModelServerClient {
      public ModelAnalyzeResponse analyze(byte[] wavBytes,
                                          String filename,
                                          String contentType,
                                          String canonicalSpaceJoined);   // canonical null/blank → 모델 서버에 part omit
      public String g2p(String text);    // 빈 입력은 short-circuit "" 반환 (MODEL_SERVER_API_SPEC §3.2)
  }
  ```
- **의존:** `RestClient modelRestClient`(§2.12).
- **에러 매핑(MODEL_SERVER_API_SPEC §3.1 errors / §4):**
  - `ResourceAccessException` → `BusinessException(MODEL_SERVER_UNAVAILABLE, e.getMessage())`.
  - `RestClientResponseException` → `BusinessException(MODEL_SERVER_ERROR, e.getResponseBodyAsString())`.
- **DTO:**
  ```java
  public record ModelAnalyzeResponse(
      List<String> perceived,
      List<String> canonical,
      List<Double> peakSoftmax,
      List<AlignmentItem> alignment,
      List<AlignmentItem> errors,
      Double per,
      Double durationSec
  ) {
      public record AlignmentItem(String op, Integer canonicalIndex, String canonical,
                                  Integer recognizedIndex, String recognized) {}
  }

  public record ModelG2pResponse(String phonemes, List<Word> words) {
      public record Word(String word, List<String> phonemes) {}
  }
  ```

### 4.2 `external.llm.LlmClient`

- **분리 정책:** 인터페이스 + 2개 구현체. provider profile 으로 선택(`app.llm.provider = rule-based | gemini`).
- **시그니처:**
  ```java
  public interface LlmClient {
      RecordingGuidance summarizeRecording(LlmContext ctx);   // RecordingService.upload 의 guidanceKr + wrongWords (F9 반영)
      String summarizeFeedback(LlmContext ctx);               // FeedbackService.generate 의 guidanceKr
      String retryGuidance(LlmContext ctx);                   // FeedbackService.retryWord 의 guidanceKr
      String suggestPracticeWord(LlmContext ctx);             // PracticeWordResolver fallback chain 한 단계

      record LlmContext(String targetText, List<String> perceived, List<String> canonical,
                        List<PhonemeError> errors, String weakPhoneme, double score) {}

      record RecordingGuidance(String guidanceKr, List<WrongWord> wrongWords) {
          public RecordingGuidance {
              guidanceKr = guidanceKr == null ? "" : guidanceKr;
              wrongWords = wrongWords == null ? List.of() : List.copyOf(wrongWords);
          }
      }
  }
  ```
- **구현체:**
  - `external.llm.RuleBasedLlmFeedbackGenerator` (`@Component @ConditionalOnProperty(name="app.llm.provider", havingValue="rule-based", matchIfMissing=true)`). `summarizeRecording` 은 `errors.canonicalIndex` + `targetText` word boundary 역산으로 wrongWords 채움 (LLM 호출 없이도 비-empty 가능).
  - `external.llm.GeminiLlmFeedbackGenerator` (`@Component @ConditionalOnProperty(name="app.llm.provider", havingValue="gemini")`). `summarizeRecording` 은 모델 응답을 파싱해 `RecordingGuidance` 빌드.
- 두 구현체 모두 non-null/non-empty 결과 보장 (API_SPEC_REFINED §5.4 의 `guidanceKr` non-null 계약).
- `wrongWords` 는 빈 배열 fallback 만 보장하면 contract 위반 없음 (API_SPEC_REFINED §5.3 명세: "없으면 `[]`"). rule-based 가 알고리즘적으로 비-empty 를 보장하지 못하는 입력에서도 `[]` 반환은 적법.

### 4.3 `pronunciation.tts.support.TtsClient`

- **분리 정책:** 인터페이스 + 1개 구현체(`DefaultTtsClient`). 도메인-국지적이므로 `external/` 가 아닌 `pronunciation/tts/support/` 에 배치.
- **시그니처:** §3.3.4 참고.

### 4.4 `pronunciation.recording.support.RecordingStorage`

- **분리 정책:** 인터페이스 + 1개 구현체(`LocalRecordingStorage`). S3/GCS 어댑터 추가 시 동일 인터페이스 구현.
- **시그니처:** §3.3.1 참고.
- **로컬 구현 동작:** `app.storage.local-root` 아래 `userId/yyyyMM/uuid.wav` 경로로 저장.

---

## 5. 트랜잭션 / 동시성 정책

- **모든 쓰기 service 메서드** `@Transactional`. **읽기 전용 메서드** `@Transactional(readOnly = true)`. 컨트롤러는 트랜잭션 어노테이션을 갖지 않는다.
- **Recording / Feedback 정적 팩토리 검증 시점** = INSERT 한정. ENTITIES_REFINED §2.7 / §2.8 의 strict XOR invariant 는 신규 객체 생성 경로 (정적 팩토리 3종) 에서만 application-level 로 강제하고, DB CHECK 식은 `(script_id IS NULL AND session_id IS NULL) OR ((script_id IS NULL) <> (session_id IS NULL))` 형태로 완화되어 있어 ON DELETE SET NULL 로 양쪽 NULL 이 되는 history 전이를 정상으로 받아들인다 (의미 보존은 `Recording.targetTextSnapshot` 및 `PronunciationFeedback` 본문 컬럼). 양쪽 NOT NULL 같은 raw misuse 는 여전히 DB 가 거절. 자세한 CHECK 식은 ENTITIES_REFINED §2.7 / §2.8 인용.
- **두 단계 검증(ENTITIES_REFINED §5.1)**:
  - 1단계 (service): 컨트롤러가 받은 Long ID 들을 user-scope 리포지토리(`findByIdAndUser_Id`, `findByUser_IdAndIdIn`)로 해석. cross-user ID 는 빈 결과 → 해당 도메인의 `*_NOT_FOUND` 변환.
  - 2단계 (entity 정적 팩토리): 주어진 엔티티 참조들이 서로 일관된지(`step.script == script`, `sentence.session == session`, `session.user == user`) 검증. 위반 시 `IllegalArgumentException` → `GlobalExceptionHandler` 가 `INTERNAL_ERROR` 로 매핑(이는 service 가 1단계를 빠뜨렸을 때만 발생하는 프로그래밍 에러).
- **`FeedbackService.complete` idempotency(ENTITIES_REFINED §5.3 + 본 명세서 §3.3.2 보강)**:
  1. `feedbackRepository.markCompletedAtomically(feedbackId, userId, Instant.now())` 호출 — `now` 는 service 가 주입. 같은 SQL 안에서 `completed=true` 와 `completed_at=:now` 가 동시에 set 되어 보상 시점과 attendance 캘린더 일자가 정확히 일치 (F8 반영, ENTITIES_REFINED §5.2).
  2. 영향 행 1 → `MemberService.awardCompletionRewards(userId, completionExp)` → 갱신된 `UserResponse`.
  3. 영향 행 0 → `feedbackRepository.findByIdAndUser_Id(feedbackId, userId)` 추가 read-only 조회로 분기:
     - present + `completed == true` → 가산 없이 `MemberService.getMyProfile(userId)` 반환 (idempotent — 같은 사용자가 두 번째 호출).
     - empty → `BusinessException(FEEDBACK_NOT_FOUND)` (cross-user 또는 not-exists; §7 cross-user 차단 시나리오 보존).
     - present + `completed == false` → `BusinessException(INTERNAL_ERROR)` (race window 가 좁아 발생 가능성 무시; 발생 시 atomic UPDATE 가 1을 반환했어야 하는 경로의 프로그래밍 에러).
  - read-modify-write 패턴 금지. `PronunciationFeedback` 엔티티에 `markCompleted()` 류 도메인 메서드 노출 금지.
  - zero-row 후의 `findByIdAndUser_Id` 는 read-only 분기 조회이므로 atomic UPDATE 원칙을 깨지 않는다.
  - 상세 흐름은 §3.3.2 `FeedbackService.complete` 본문 참조.
- **`Session.updateScript`** 가 sentences 컬렉션을 통째 교체(orphanRemoval) 하면 그 즉시 기존 `Recording.session_sentence_id` 가 NULL 로 끊어진다. `targetTextSnapshot` 으로 의미가 보존되므로 별도 history 테이블 필요 없음.
- **`RecordingService.upload` commit-time 보상 (Codex finding F7 반영)**: ⑧ `RecordingStorage.save` 직후 ⑨ 에서 `TransactionSynchronizationManager.registerSynchronization(...)` 으로 `afterCompletion(int status)` 콜백을 등록한다. `status != STATUS_COMMITTED` (rollback / unknown) 이면 `recordingStorage.delete(audioPath)` 호출. service 본문 try-catch 만으로는 잡을 수 없는 commit-time 실패 (JPA flush 시 DB constraint 위반, 트랜잭션 매니저 commit 실패 등) 까지 단일 정책으로 정리되며, 본문 / flush / commit / unknown 의 모든 비-commit 경로가 같은 cleanup 을 거친다. cleanup 자체의 2차 실패는 try-catch 로 삼키고 WARN 로그만 — 원본 트랜잭션 결과를 가리지 않도록.

---

## 6. 의존성 그래프

같은 feature 내 controller → service → repository 화살표는 생략하고, **cross-feature / cross-layer 호출만** 표시한다.

```
member.AuthService              ──► UserRepository, JwtProvider, PasswordEncoder
member.MemberService            ──► UserRepository
                                ◄── (호출됨) FeedbackService.complete, AuthService.signup

learning.track.TrackService     ──► TrackRepository, ScriptRepository(read)
learning.script.ScriptService   ──► ScriptRepository, LearningStepRepository, RecommendedScriptSelector
                                ◄── (호출됨) RecordingService(loadScript/loadStep), FeedbackService(loadScript), RankingService
learning.session.SessionService ──► SessionRepository, SessionSentenceRepository, SentenceSplitter, MemberService
                                ◄── (호출됨) RecordingService(loadOwnedSession/loadOwnedSentence), FeedbackService(loadOwnedSession)

pronunciation.recording.RecordingService
                                ──► RecordingRepository, MultipartAudioReader, RecordingStorage,
                                    ModelServerClient, LlmClient, ScoringPolicy, WeakPhonemeAnalyzer,
                                    PhonemeErrorMapper, MemberService.loadUser, ScriptService, SessionService
                                ◄── (호출됨) FeedbackService(loadOwnedRecordings)

pronunciation.feedback.FeedbackService
                                ──► FeedbackRepository, RecordingRepository(read), ScriptService, SessionService,
                                    MemberService(loadUser, awardCompletionRewards),
                                    ModelServerClient, LlmClient, ScoringPolicy, PracticeWordResolver,
                                    WeakPhonemeAnalyzer, PhonemeErrorMapper, MultipartAudioReader

pronunciation.tts.TtsService    ──► TtsClient

statistics.stats.StatsService   ──► UserRepository, RecordingRepository(read), FeedbackRepository(read),
                                    BadgePolicy, WeakPhonemeAnalyzer
statistics.ranking.RankingService
                                ──► DemoRankingEntryRepository, FeedbackRepository(read), ScriptService

system.HealthController         ──► (없음)
```

- `external.ModelServerClient` 는 `RecordingService`, `FeedbackService` 에서만 호출.
- `external.LlmClient` 는 `RecordingService`, `FeedbackService`, `PracticeWordResolver` 에서 호출.
- `pronunciation.tts.TtsClient` 는 `TtsService` 에서만 호출.

---

## 7. 검증

ENTITIES_REFINED §5.4 의 6개 검증 시나리오가 어느 컴포넌트 경계에 배치되는지 명시.

| 검증 시나리오 | 배치 레이어 | 테스트 종류 |
| --- | --- | --- |
| Cross-user 차단 (사용자 A 의 토큰으로 B 의 sessionId/recordingId/feedbackId 호출 → 404) | service (user-scope repo 메서드의 빈 결과 → `*_NOT_FOUND`) | `@SpringBootTest` + MockMvc, Recording / Feedback / Session 각각 |
| Cross-parent 거절 (Recording 정적 팩토리: `step.script != script`, `sentence.session != session`, `session.user != user`) | entity factory | `RecordingTest` (pure unit, no Spring) |
| Cross-parent 거절 (`PronunciationFeedback.create`: session-flow 에서 `session.user != user`) | entity factory | `PronunciationFeedbackTest` (pure unit) |
| Recording 정합성 CHECK 제약 (script 와 session 동시 NOT NULL 등) raw INSERT 시 DB 에서 거절 | DB / Hibernate `@Check` | `@DataJpaTest` integration |
| Session 대본 갱신 후 녹음 보존 (`Recording.session_sentence_id` 는 NULL, `targetTextSnapshot` 보존) | service + DB | `@SpringBootTest` integration, `SessionService.patch` 호출 후 `RecordingRepository` 조회 |
| Session 하드 삭제 후 history 보존 (Recording / Feedback 의 `session_id` NULL, 본문 보존) | service + DB | 위와 동일 흐름, `delete` 호출 |
| 완료 동시성 (`complete` 두 스레드 동시 호출 → EXP 정확히 한 번 가산) | service | `CountDownLatch` 기반 동시성 테스트, `FeedbackService.complete` |
| Cross-context generate 거절 (사용자 자기 소유 recording 이지만 다른 script/session ID 를 섞어 호출 → 404 `RECORDING_NOT_FOUND`. Codex finding F1 회귀 방지) | service (컨텍스트-스코프 repo 메서드의 size 미스매치) | `@SpringBootTest` + MockMvc |
| ON DELETE SET NULL 로 부모 끊긴 recording 의 generate 거절 (session 삭제 후 그 session 에 속했던 recording 들로 호출 → 동일하게 `RECORDING_NOT_FOUND`) | service | `@SpringBootTest` integration |
| Cross-user complete 거절 (user A 의 토큰으로 user B 의 feedbackId 호출 → 404 `FEEDBACK_NOT_FOUND`, 보상 가산 없음. Codex finding F2 회귀 방지) | service (atomic UPDATE 0 → existence check empty → 404) | `@SpringBootTest` + MockMvc |
| Recording upload 부분 실패 시 storage 정리 (model server timeout / 정적 팩토리 검증 실패 / DB save 실패 각각 케이스 → 디스크에 orphan WAV 잔존 0. Codex finding F3 회귀 방지) | service + storage | `@SpringBootTest` + 임시 디렉토리 + 모델 서버 mock |
| Recording upload commit-time 실패 시 storage 정리 (Recording flush 시 DB constraint 위반 / 트랜잭션 매니저 commit 강제 실패 → afterCompletion(STATUS_ROLLED_BACK) → 디스크에 orphan WAV 잔존 0. Codex finding F7 회귀 방지) | service + storage | `@SpringBootTest` + 임시 디렉토리 + 강제 실패 `TransactionSynchronization` 또는 mocked DataIntegrityViolation |
| Attendance 일자 정확도 (day=N 에 generate 한 feedback 을 day=N+2 에 complete → `/api/stats/me?year=Y&month=M` 의 `attendance.days` 가 N+2 위치에서 누적 streak 1 증가, [N] 영향 없음. Codex finding F8 회귀 방지) | service + repository | `@SpringBootTest` + Clock 주입, `FeedbackRepository.findCompletedDaysInMonth` 직접 검증 |
| wrongWords 비/empty (errors 가 있는 녹음의 응답 `wrongWords` 가 비-empty / errors 가 빈 녹음의 응답이 `[]`. Codex finding F9 회귀 방지) | service | `@SpringBootTest` + LLM mock, `RecordingResponse.wrongWords` 검증 |

추가로 controller 별 REST Docs 스니펫 테스트를 작성해 `API_SPEC_REFINED.md` 응답 예시와 일치 여부를 회귀 방지.

---

## 부록 A. API ↔ Controller ↔ Service ↔ Repository 매핑 표

26개 endpoint × 컬럼.

| # | 도메인 | HTTP | 경로 | Controller#method | Service#method | 주요 Repository methods | 사용 Entities |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Member | POST | `/api/auth/signup` | `AuthController.signup` | `AuthService.signup` | `UserRepository.existsByUsername/Email`, `save` | `User` |
| 2 | Member | POST | `/api/auth/login` | `AuthController.login` | `AuthService.login` | `UserRepository.findByUsername` | `User` |
| 3 | Member | POST | `/api/auth/check-username` | `AuthController.checkUsername` | `AuthService.isUsernameAvailable` | `UserRepository.existsByUsername` | `User` |
| 4 | Member | POST | `/api/auth/check-email` | `AuthController.checkEmail` | `AuthService.isEmailAvailable` | `UserRepository.existsByEmail` | `User` |
| 5 | Member | GET | `/api/auth/oauth2/google/demo` | `AuthController.googleDemo` | `AuthService.loginGoogleDemo` | `UserRepository.findByEmail`, `save` | `User` |
| 6 | Member | GET | `/api/members/me` | `MemberController.me` | `MemberService.getMyProfile` | `UserRepository.findById` | `User` |
| 7 | Member | PATCH | `/api/members/me/nickname` | `MemberController.updateNickname` | `MemberService.changeNickname` | `UserRepository.findById` | `User` |
| 8 | Learning | GET | `/api/tracks` | `TrackController.list` | `TrackService.listAll` | `TrackRepository.findAllByOrderByDisplayOrderAsc`, `ScriptRepository.findByTrack_Id...` (count) | `Track`, `Script` |
| 9 | Learning | GET | `/api/tracks/{trackId}` | `TrackController.detail` | `TrackService.getDetail` | `TrackRepository.findById`, `ScriptRepository.findByTrack_IdOrderByChapterOrderAsc` | `Track`, `Script` |
| 10 | Learning | GET | `/api/scripts/recommended/today` | `ScriptController.recommendedToday` | `ScriptService.recommendedToday` | `ScriptRepository.findByPresetTrue...` (정책 기반) | `Script` |
| 11 | Learning | GET | `/api/scripts/{scriptId}` | `ScriptController.detail` | `ScriptService.getDetail` | `ScriptRepository.findById`, `LearningStepRepository.findByScript_IdOrderByOrderIndexAsc` | `Script`, `LearningStep` |
| 12 | Learning | GET | `/api/sessions` | `SessionController.list` | `SessionService.listMine` | `SessionRepository.findByUser_IdOrderByFavoriteDescUpdatedAtDesc` | `Session`, `SessionSentence` |
| 13 | Learning | POST | `/api/sessions` | `SessionController.create` | `SessionService.create` | `SessionRepository.save` | `Session` |
| 14 | Learning | GET | `/api/sessions/{sessionId}` | `SessionController.detail` | `SessionService.getMine` | `SessionRepository.findByIdAndUser_Id` | `Session`, `SessionSentence` |
| 15 | Learning | PATCH | `/api/sessions/{sessionId}` | `SessionController.patch` | `SessionService.patch` | `SessionRepository.findByIdAndUser_Id` | `Session`, `SessionSentence` |
| 16 | Learning | DELETE | `/api/sessions/{sessionId}` | `SessionController.delete` | `SessionService.delete` | `SessionRepository.findByIdAndUser_Id`, `delete` | `Session`, `SessionSentence` |
| 17 | Pronunciation | POST | `/api/recordings` | `RecordingController.upload` | `RecordingService.upload` | `RecordingRepository.save`, `SessionRepository.findByIdAndUser_Id`, `SessionSentenceRepository.findByIdAndSession_User_Id`, `ScriptRepository.findById`, `LearningStepRepository.findById`, `LlmClient.summarizeRecording`, `RecordingStorage.save` / `delete`, `TransactionSynchronizationManager.registerSynchronization` | `Recording`, `Script`, `LearningStep`, `Session`, `SessionSentence`, `User` |
| 18 | Pronunciation | POST | `/api/feedback/generate` | `FeedbackController.generate` | `FeedbackService.generate` | `FeedbackRepository.save`, `RecordingRepository.findByUser_IdAndScript_IdAndIdIn` / `findByUser_IdAndSession_IdAndIdIn`, `ScriptRepository.findById`, `SessionRepository.findByIdAndUser_Id` | `PronunciationFeedback`, `PhonemeError`, `Recording`, `Script`/`Session`, `User` |
| 19 | Pronunciation | POST | `/api/feedback/{feedbackId}/retry-word` | `FeedbackController.retryWord` | `FeedbackService.retryWord` | `FeedbackRepository.findByIdAndUser_Id` | `PronunciationFeedback` |
| 20 | Pronunciation | POST | `/api/feedback/{feedbackId}/complete` | `FeedbackController.complete` | `FeedbackService.complete` | `FeedbackRepository.markCompletedAtomically(id, userId, now)`, `FeedbackRepository.findByIdAndUser_Id` (zero-row 분기), `UserRepository.findById` | `PronunciationFeedback`, `User` |
| 21 | Pronunciation | GET | `/api/feedbacks` | `FeedbacksReadController.list` | `FeedbackService.listMine` | `FeedbackRepository.findByUser_IdOrderByCreatedAtDesc` | `PronunciationFeedback` |
| 22 | Pronunciation | GET | `/api/feedbacks/{feedbackId}` | `FeedbacksReadController.detail` | `FeedbackService.getMine` | `FeedbackRepository.findByIdAndUser_Id` | `PronunciationFeedback`, `PhonemeError` |
| 23 | Pronunciation | POST | `/api/tts` | `TtsController.synthesize` | `TtsService.synthesize` | (없음) | (없음) |
| 24 | Statistics | GET | `/api/stats/me` | `StatsController.me` | `StatsService.getMyStats` | `UserRepository.findById`, `RecordingRepository.*` (주간 약점 음소), `FeedbackRepository.findCompletedDaysInMonth` (월간 attendance group-by), `FeedbackRepository.*` (누적 완료 수) | `User`, `Recording`, `PronunciationFeedback`, `PhonemeError` |
| 25 | Statistics | GET | `/api/ranking/today` | `RankingController.today` | `RankingService.today` | `DemoRankingEntryRepository.findAll`, `FeedbackRepository.*` | `DemoRankingEntry`, `PronunciationFeedback`, `User` |
| 26 | System | GET | `/api/health` | `HealthController.health` | (없음) | (없음) | (없음) |

---

## 부록 B. ErrorCode 발생 위치 매트릭스

19개 ErrorCode × 발생 컴포넌트.

| ErrorCode | 발생 위치 | 발생 트리거 |
| --- | --- | --- |
| `INVALID_REQUEST` | `RecordingService.upload`, `FeedbackService.generate`, `TtsService.synthesize`, `StatsService.getMyStats`, `GlobalExceptionHandler` (MaxUploadSize / MissingPart) | 컨텍스트 조합 위반, scriptId XOR sessionId 위반, year/month 범위 위반, multipart 누락/초과 |
| `VALIDATION_FAILED` | `GlobalExceptionHandler` (`MethodArgumentNotValidException`) | request DTO `@Valid` 위반 (전 endpoint 자동 매핑) |
| `UNAUTHORIZED` | `JwtAuthEntryPoint`, `GlobalExceptionHandler` (Spring `AuthenticationException`/`AccessDeniedException`) | JWT 누락/거부 |
| `INVALID_TOKEN` | `JwtProvider.parse` → `BusinessException` | JWT 만료/서명 위반 |
| `LOGIN_FAILED` | `AuthService.login` | username 없음 또는 BCrypt mismatch |
| `USERNAME_DUPLICATED` | `AuthService.signup` | `UserRepository.existsByUsername` true |
| `EMAIL_DUPLICATED` | `AuthService.signup` | `UserRepository.existsByEmail` true |
| `USER_NOT_FOUND` | `MemberService.getMyProfile / changeNickname / loadUser / awardCompletionRewards`, `StatsService.getMyStats` | `UserRepository.findById` 빈 결과 |
| `TRACK_NOT_FOUND` | `TrackService.getDetail` | `TrackRepository.findById` 빈 결과 |
| `SCRIPT_NOT_FOUND` | `ScriptService.getDetail / loadScript`, `RecordingService.upload`, `FeedbackService.generate` | `ScriptRepository.findById` 빈 결과 |
| `STEP_NOT_FOUND` | `ScriptService.loadStep`, `RecordingService.upload` | `LearningStepRepository.findById` 빈 결과 |
| `SESSION_NOT_FOUND` | `SessionService.getMine / patch / delete / loadOwnedSession`, `RecordingService.upload`, `FeedbackService.generate` | `SessionRepository.findByIdAndUser_Id` 빈 결과 |
| `SESSION_SENTENCE_NOT_FOUND` | `SessionService.loadOwnedSentence`, `RecordingService.upload` | `SessionSentenceRepository.findByIdAndSession_User_Id` 빈 결과 |
| `RECORDING_NOT_FOUND` | `RecordingService.loadOwnedRecording / loadOwnedRecordings`, `FeedbackService.generate` | user-scope 결과가 입력 ID 수와 불일치. `generate` 의 경우 cross-user / cross-context (다른 script/session 의 recording 혼합) / `ON DELETE SET NULL` 로 부모 끊긴 recording 모두 동일 코드로 통합. |
| `FEEDBACK_NOT_FOUND` | `FeedbackService.retryWord / complete / getMine` | `FeedbackRepository.findByIdAndUser_Id` 빈 결과. `complete` 는 atomic UPDATE 영향 행 0 후 `findByIdAndUser_Id` 추가 조회로 cross-user/not-exists 케이스를 분기해 본 코드 throw. |
| `AUDIO_DECODE_FAILED` | `MultipartAudioReader.read` (호출자: `RecordingService`, `FeedbackService.retryWord`) | WAV 변환/duration 추출 실패 |
| `MODEL_SERVER_UNAVAILABLE` | `ModelServerClient.analyze / g2p` | `ResourceAccessException` (timeout, refused) |
| `MODEL_SERVER_ERROR` | `ModelServerClient.analyze / g2p` | 비-2xx 응답 |
| `INTERNAL_ERROR` | `GlobalExceptionHandler` (catch-all) | 위 매핑 외 모든 예외 (entity 정적 팩토리의 `IllegalArgumentException` 도 여기로 매핑 — 이는 service 가 두 단계 검증을 빠뜨렸을 때만 발생하는 프로그래밍 에러) |

---

## 부록 C. ModelServerClient 호출 매트릭스

| 모델 op | 호출 service | 호출 시점 |
| --- | --- | --- |
| `POST /analyze` | `RecordingService.upload` | 학습 단계 녹음 분석 (canonical 동봉) |
| `POST /analyze` | `FeedbackService.retryWord` | 약점 단어 재시도 분석 (canonical 동봉) |
| `POST /g2p` | `RecordingService.upload` | targetText 가 있는 모드(`script-flow` / `session-sentence`)에서 canonical 산출 |
| `POST /g2p` | `FeedbackService.retryWord` | feedback.practiceWord → canonical 산출 |

`FeedbackService.generate` 는 모델 서버를 직접 호출하지 않고, 이미 분석되어 저장된 `Recording.errors_json` / `perceived` / `canonical` 캐시를 활용해 종합 피드백을 만든다.

---

## 부록 D. 한 줄 정책 인덱스

본문에서 시그니처만 적고 본문 알고리즘은 생략한 항목들의 한 줄 정책. 각 항목의 정식 본문은 구현 시점에 별도 정책 문서로 분리한다.

- **`ScoringPolicy.aggregate(List<Recording>)`** — 각 Recording 의 `stepScore` 평균. null 점수는 분모에서 제외. 결과 `[0, 100]`.
- **`ScoringPolicy.singleWordScore(ModelAnalyzeResponse)`** — `peakSoftmax` 평균을 기본으로 하고, alignment 의 substitution/insertion/deletion 비율로 감점.
- **`PracticeWordResolver` fallback chain** — ① `Script.practiceWord` → ② 시드 챕터의 단어 풀 → ③ `LlmClient.suggestPracticeWord` → ④ 약점 음소 키 매핑 표 → ⑤ `app.feedback.default-practice-word`. 첫 non-blank 결과 채택. 항상 non-null 보장(API_SPEC_REFINED §5.4).
- **`WeakPhonemeAnalyzer.topOne(List<Recording>)`** — 모든 `errors_json` 의 op != equal 항목에서 canonical 음소 빈도 집계 → top 1. 동률은 가장 자주 나타난 순.
- **`WeakPhonemeAnalyzer.topN(...)`** — 동일 알고리즘으로 N개. stats 의 `weeklyErrors` 에서 N=5 권장.
- **`BadgePolicy.evaluate(...)`** — 정의된 배지 ID 집합(예: `FIRST_FEEDBACK`, `STREAK_7`, `WEAK_PHONEME_TAMER`)에 대해 user 의 streak/완료 수/주간 약점 정복 여부를 임계와 비교.
- **`RecommendedScriptSelector.select(today, userId)`** — 시드 (preset=true) 스크립트 풀에서 오늘 날짜 기반 결정적 셔플로 N개 선정. 사용자별 직전 학습 스크립트는 우선순위 하향.
- **`SentenceSplitter.split(scriptText)`** — 종지부호(`.!?`) 와 줄바꿈을 1차 분할 키로 사용. 약어(예: `Mr.`) 는 휴리스틱으로 보존. 결과는 trim, 빈 문장 제거.
- **`User.streak` 7 캡** — `User.recordCompletion` 본문에서 7 도달 시 더 이상 증가하지 않음. 어제 학습 → +1, 그 외 → 1 리셋.
- **`RecordingStorage.delete`** — 멱등. 없는 path 도 silent OK. 실패는 WARN 로그만, 본 예외를 throw 하지 않아 보상 경로의 2차 실패가 원본 예외(`RecordingService.upload` 의 DB save 예외 등)를 가리지 않는다.
- **`RecordingStorage.save` 호출 위치** — `RecordingService.upload` 의 ⑧ 단계. 정적 팩토리(⑩) 가 결과 `audioPath` 를 인자로 받으므로 save 가 factory 보다 반드시 앞에 와야 한다. ④ 의 `validateForXxx` 가 cross-parent invariant 를 객체 생성 전에 미리 통과시켜 두므로 ⑧–⑩ 사이의 storage write 가 invariant 위반 때문에 orphan 으로 남을 가능성은 없다.
- **`RecordingStorage` commit-time 보상** — `RecordingService.upload` ⑨ 단계에서 등록한 `TransactionSynchronization.afterCompletion(int status)` 가 `status != STATUS_COMMITTED` 일 때 `recordingStorage.delete(audioPath)` 호출. 본문 예외 / flush 예외 / commit 예외 / 알 수 없는 결과 모두 같은 경로로 정리됨. cleanup 자체의 2차 실패는 try-catch 로 삼키고 WARN 로그만 — 원본 트랜잭션 결과를 가리지 않도록.
- **`Recording.validateForXxx` 정적 메서드** — `RecordingService.upload` 흐름 ④ 단계에서 `audioPath` 가 결정되기 전 cross-parent 검증을 수행. 정적 팩토리(`forXxx`) 자체의 invariant (ENTITIES_REFINED §2.7) 는 변경 없이 유지되며, ⑩ 의 객체 생성 시점에 같은 invariant 가 한 번 더 검증됨 (이중 안전).
- **`LlmClient.summarizeRecording`** — `RecordingGuidance(guidanceKr, wrongWords)` 반환. rule-based 구현은 `errors.canonicalIndex` + `targetText` word boundary 역산으로 wrongWords 채움 (LLM 호출 없이도 비-empty 가능). gemini provider 는 LLM 응답 파싱. 두 구현 모두 빈 배열 fallback 보장 (API_SPEC_REFINED §5.3 contract: "없으면 `[]`").
- **`StatsService.attendance` group-by** — `FeedbackRepository.findCompletedDaysInMonth(userId, monthStartUtc, monthEndUtc)` 가 `completed_at` 컬럼 기반 일자별 count 반환 → service 가 누적 streak 변환 (출석 없는 날은 응답 map 에서 키 omit). 타임존 환산 (`year`/`month` → `monthStartUtc`/`monthEndUtc`) 은 service 책임, `app.stats.zone` 기본 `Asia/Seoul`. NULL `completed_at` (rev4 이전 history 행) 자동 제외.
- **`FeedbackService.complete` now 주입** — `Instant.now()` 를 service 에서 만들어 `markCompletedAtomically(id, userId, now)` 인자로 전달. 같은 SQL 안에서 `completed=true` 와 `completed_at=:now` 가 동시에 set 되어 보상 시점과 attendance 캘린더 일자가 정확히 일치 (ENTITIES_REFINED §5.2).
- **`resolveParents` 책임** — `RecordingService.upload` 흐름 ① 단계의 단일 helper. `memberService.loadUser` (USER_NOT_FOUND) → `detectMode` (INVALID_REQUEST) → mode 별 user-scope 부모 조회 (`*_NOT_FOUND`) 를 순서대로 수행하고 `sealed interface ResolvedParents` 의 3 record 변종 중 하나로 반환. cross-parent 일관성은 ④ `validateForXxx` 와 ⑩ 정적 팩토리가 책임.

---

## 부록 E. 개정 이력

| 일자 | 항목 |
| --- | --- |
| 2026-05-10 | 초판. 26개 endpoint / 10개 entity / 19개 ErrorCode / 2개 모델 서버 op 전부에 대한 컴포넌트·시그니처 매핑. |
| 2026-05-10 (rev2) | Codex adversarial review 반영 — (F1) `FeedbackService.generate` 의 컨텍스트 정합 검증을 위해 `RecordingRepository` 에 `findByUser_IdAndScript_IdAndIdIn` / `findByUser_IdAndSession_IdAndIdIn` 신설, (F2) `FeedbackService.complete` 의 atomic UPDATE 영향 행 0 케이스를 not-exists/cross-user(`FEEDBACK_NOT_FOUND`) 와 already-completed(idempotent) 로 분기, (F3) `RecordingService.upload` 흐름 재정렬 (`RecordingStorage.save` 마지막 직전으로 이동) + `RecordingStorage.delete` 보상 메서드 신설 + `Recording.validateForXxx` 검증 전용 정적 메서드 신설. |
| 2026-05-10 (rev3) | Codex round 2 반영 — (F4) `RecordingService.upload` 흐름 ⑨ ↔ ⑩ 재정렬: `RecordingStorage.save` 가 정적 팩토리보다 앞 (⑨), 정적 팩토리(⑩) 가 결과 `audioPath` 를 인자로 받음. ⑩–⑪ 의 try-catch 가 정적 팩토리 + applyAnalysis + repo.save 를 함께 보호하고 catch 시 `RecordingStorage.delete` 보상. (F5) Recording / PronunciationFeedback 의 CHECK 식 완화 (`(script_id IS NULL AND session_id IS NULL) OR XOR`) — ENTITIES_REFINED §2.7 / §2.8 동시 갱신. INSERT 시점 strict XOR 은 정적 팩토리 3종이 application-level 로 보장. (F6) §5 `FeedbackService.complete` idempotency 4-줄 요약을 §3.3.2 rev2 의 3-way 분기 (present+completed=true / empty / present+completed=false) 로 동기화. |
| 2026-05-10 (rev4) | Codex round 3 반영 — (F7) `RecordingService.upload` 의 본문 try-catch 를 제거하고 ⑨ 단계에서 `TransactionSynchronization.afterCompletion` 등록으로 commit-time 실패까지 (본문 / flush / commit / unknown) 단일 정책으로 storage 정리. `resolveParents` helper (sealed interface `ResolvedParents` + 3 record + `detectMode` private) 도입으로 ① mode 분기 + user-scope 부모 조회 + 사용자 로드를 단일 경로로 통합. 흐름이 12-step → 11-step 으로 정리. (F8) `PronunciationFeedback.completed_at` 컬럼 + `(user_id, completed_at)` 인덱스 신설, `markCompletedAtomically` 가 `now` 인자 받아 같은 UPDATE 에서 set, `StatsService.attendance` 는 `findCompletedDaysInMonth` group-by 로 정확한 일자 산출. ENTITIES_REFINED §2.8 / §5.2 / §5.3 / §5.4 동시 갱신. (F9) `LlmClient.summarizeRecording` 신설 — `RecordingGuidance(guidanceKr, wrongWords)` 반환. `Recording.wrong_words_json` 컬럼 + `AnalysisOutcome.wrongWordsJson` 추가 (ENTITIES_REFINED §2.7 동시 갱신). `RecordingResponse.from` 이 JSON 역직렬화로 `WrongWord[]` 노출, NULL/실패는 빈 배열 fallback. |

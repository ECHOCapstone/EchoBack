# API_TEST_PLAN.md — TDD 재구현용 자기완결 회귀 방지 테스트 플랜

## §0. 메타

- **목적**: `main` 브랜치 (root Gradle 프로젝트) 에 새로 구현될 backend 가 `API_SPEC_REFINED.md` 의 contract 를 회귀 없이 만족함을 자동 검증한다.
- **Contract source-of-truth**: `API_SPEC_REFINED.md` (새 contract). `API_SPEC.md` (기존) 와 다른 부분이 있다면 본 plan 은 **REFINED 를 따른다**.
- **회귀 방지 정의**: 같은 (HTTP method, path, request body/query/path/headers) 입력에 대해 `API_SPEC_REFINED.md` 가 명시한 (status, response body schema, ErrorCode, headers) 가 나오면 회귀 없음.
- **자기완결 약속**: 본 문서 외부 (다른 SPEC 문서, 다른 브랜치의 코드, GitHub PR 등) 를 한 번도 참조하지 않고도 모든 테스트 작성이 가능해야 한다. REFINED 의 차이는 본 문서 본문에 인라인되어 있으며, 본 문서가 source-of-truth.
- **nullable 표기 정책**: 본 문서의 응답 예시에서 `?` 접미사가 붙은 필드 또는 `@JsonInclude(NON_NULL)` 정책 대상 필드는 null 일 때 응답 JSON 에서 키 자체가 omit 된다 (REFINED §1.3). 골든 assertion 은 키 존재 (`exists()`) / 부재 (`doesNotExist()`) 를 명시적으로 검증한다.
- **사용 방법**: §3 의 한 endpoint 항목을 한 controller 테스트 클래스로, §4 의 한 invariant 항목을 한 service 테스트 메서드로 그대로 옮긴다.
- **테스트 스택**: JUnit 5 + Spring Boot Test + MockMvc + Spring REST Docs (MockMvc) + H2 in-memory + Spring Security Test. LLM/모델 서버는 `@MockBean` 으로 격리. 멀티파트는 `MockMultipartFile`.
- **2-tier 정책**:
  - **Tier 1** (§3) — Controller MockMvc 회귀: 26 endpoint × HTTP contract.
  - **Tier 2** (§4) — Service-level domain invariant: cross-user / atomic UPDATE / storage commit-time 보상 / KST 자정 등 multi-step 행동 invariant.

---

## §1. 테스트 인프라 셋업

`main` 브랜치는 root Gradle 프로젝트 (`build.gradle`, `settings.gradle` 옆 `src/main/...`, `src/test/...` 표준 layout) 이며 backend 구현이 비어있는 상태에서 시작한다. 본 절은 그 시점의 최소 테스트 셋업 본문 — 구현팀이 그대로 복사해 시작할 수 있도록.

### §1.1 build.gradle 의존 + 플러그인 (전체 본문)

`main` 의 root `build.gradle` (settings.gradle 옆) 에 다음 블록을 병합한다. 추가 모듈 / sub-project 없음 — root Gradle 단일 프로젝트.

```groovy
plugins {
    id 'org.asciidoctor.jvm.convert' version '3.3.2'
}

ext {
    snippetsDir = file('build/generated-snippets')
}

dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.springframework.restdocs:spring-restdocs-mockmvc'
    testRuntimeOnly  'com.h2database:h2'
}

test {
    outputs.dir snippetsDir
    useJUnitPlatform()
}

asciidoctor {
    inputs.dir snippetsDir
    dependsOn test
}
```

### §1.2 application-test.yaml (전체 본문)

`src/test/resources/application-test.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        jdbc:
          time_zone: Asia/Seoul
        format_sql: true
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

app:
  cors:
    allowedOrigins:
      - http://localhost:3000
  jwt:
    secret: test-secret-32-bytes-minimum-aaaaaaaa
    accessTokenTtlSec: 3600
  modelServer:
    baseUrl: http://localhost:0
    timeoutMs: 1000
  feedback:
    defaultPracticeWord: water
    completionExp: 10
  storage:
    localRoot: build/tmp/recordings
  llm:
    provider: rule-based
  tts:
    provider: stub
  stats:
    zone: Asia/Seoul
```

### §1.3 베이스 클래스

#### `AbstractControllerIntegrationTest` (Tier 1)

```java
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@ActiveProfiles("test")
@Transactional
public abstract class AbstractControllerIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected JwtProvider jwtProvider;

    @MockBean protected ModelServerClient modelServerClient;
    @MockBean protected LlmClient llmClient;

    /** Bearer 토큰 한 줄 헬퍼 — header("Authorization", bearer(userId)). */
    protected String bearer(Long userId) {
        return "Bearer " + jwtProvider.issue(userId, "user" + userId);
    }

    /** 응답이 ApiResponse 의 success=true 인지 검증 (`error` 는 NON_NULL 로 omit). */
    protected ResultMatcher apiSuccess() {
        return result -> {
            jsonPath("$.success").value(true).match(result);
            jsonPath("$.error").doesNotExist().match(result);
        };
    }

    /** 응답이 ApiResponse 의 success=false 이고 지정 ErrorCode 를 반환하는지 검증. */
    protected ResultMatcher apiError(String code) {
        return result -> {
            jsonPath("$.success").value(false).match(result);
            jsonPath("$.data").doesNotExist().match(result);
            jsonPath("$.error.code").value(code).match(result);
        };
    }
}
```

#### `AbstractServiceIntegrationTest` (Tier 2)

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractServiceIntegrationTest {
    @MockBean protected ModelServerClient modelServerClient;
    @MockBean protected LlmClient llmClient;
    // 필요 service 들 @Autowired 로 직접 주입 (MockMvc 우회).
}
```

### §1.4 Fixture 빌더

| Fixture | 메서드 | 비고 |
| --- | --- | --- |
| `UserFixture` | `seedUserA()`, `seedUserB()` | username=alice/bob, password=`Pa$$w0rd!`, 시드 후 ID 반환 |
| `ScriptFixture` | `seedPresetScript(Difficulty)`, `seedScriptWithSteps(int stepCount)` | preset=true, steps=[INTRO, RECORD×N] |
| `LearningStepFixture` | `seedRecordStep(Script, String targetText)` | kind=RECORD |
| `SessionFixture` | `seedSession(User, String title, String scriptText)` | sentences 자동 split |
| `RecordingFixture` | `seedScriptFlowRecording(...)`, `seedSessionFlowRecording(...)` | analysis 까지 채움 |
| `FeedbackFixture` | `seedScriptFlowFeedback(...)`, `seedSessionFlowFeedback(...)` | completed=false 기본 |
| `WavFixtures` | `SILENT_1S` (32044 bytes, 16kHz mono PCM), `WATER_1S` | resource `/test-wav/silent-1s.wav` 등 |
| `AnalyzeMockResponses` | `WATER_PERFECT`, `WATER_WITH_ERROR` | `ModelAnalyzeResponse` 인스턴스 |
| `LlmMockResponses` | `GUIDANCE_PERFECT`, `GUIDANCE_SIMPLE` | `RecordingGuidance` / `String` |

> Resource 경로 (`/test-wav/silent-1s.wav` 등) 는 root Gradle layout 기준 `src/test/resources/test-wav/...` 로 해석된다.

### §1.5 JWT 발급 / 인증 contract

- **헤더 형식**: `Authorization: Bearer <jwt>`. JWT 알고리즘 HS256, secret 은 `app.jwt.secret`.
- **JWT claims**: `sub`=username, `uid`=userId(Long), `iat`, `exp`. 검증 측은 secret 으로 서명 검증 후 `JwtPrincipal(userId, username)` 반환.
- **401 응답 분리** (REFINED §2.2):
  - **missing token** (Authorization 헤더 부재) → 401 + `{ "success": false, "error": { "code": "UNAUTHORIZED", ... } }`. JwtAuthFilter 가 SecurityContext 를 비우면 Spring `AuthenticationException` → GlobalExceptionHandler → UNAUTHORIZED.
  - **malformed token** (서명 깨짐 / 구조 깨짐 / 알고리즘 불일치) → 401 + `error.code="INVALID_TOKEN"`. JwtAuthFilter 가 파싱 실패를 INVALID_TOKEN BusinessException 으로 throw.
  - **expired token** (`exp` 가 현재 시각 이전) → 401 + `error.code="INVALID_TOKEN"`.
  - 모든 401 응답: Content-Type `application/json; charset=UTF-8`, `data` 키 omit (`@JsonInclude(NON_NULL)`).
- **회귀 기준**: missing 과 malformed/expired 가 서로 다른 ErrorCode 로 응답되어야 한다. 둘을 구분하지 않고 하나의 코드로만 응답하는 구현은 회귀 위반.

#### §1.5.1 보호 endpoint 일괄 401 검증 (parametrized)

본 plan 은 20개 보호 endpoint (auth/health 제외) 모두에 대해 missing-token / malformed-token / expired-token 3 시나리오를 일괄 검증하는 단일 parametrized 테스트를 명세한다. per-endpoint §3 시나리오는 기존 `unauthorized` 한 줄 + `INVALID_TOKEN — see §1.5.1` 한 줄로 본 테스트를 참조.

**테스트 클래스**: `auth.ProtectedEndpointAuthContractTest` (Tier 1).

**대상 dispatch 표 (20개, minimal request)**:

| HTTP method + path | minimal request body / params |
| --- | --- |
| `GET /api/members/me` | (없음) |
| `PATCH /api/members/me/nickname` | `{"nickname":"x"}` |
| `GET /api/tracks` | (없음) |
| `GET /api/tracks/1` | (없음) |
| `GET /api/scripts/recommended/today` | (없음) |
| `GET /api/scripts/1` | (없음) |
| `GET /api/sessions` | (없음) |
| `POST /api/sessions` | `{"title":"x"}` |
| `GET /api/sessions/1` | (없음) |
| `PATCH /api/sessions/1` | `{"favorite":true}` |
| `DELETE /api/sessions/1` | (없음) |
| `POST /api/recordings` | multipart `audio` part + `?scriptId=1&stepId=1` |
| `POST /api/feedback/generate` | `{"scriptId":1,"recordingIds":[1]}` |
| `POST /api/feedback/1/retry-word` | multipart `audio` part |
| `POST /api/feedback/1/complete` | (없음) |
| `GET /api/feedbacks` | (없음) |
| `GET /api/feedbacks/1` | (없음) |
| `GET /api/stats/me` | (없음) |
| `GET /api/ranking/today` | (없음) |
| `POST /api/tts` | `{"text":"hi"}` |

> **중요**: 401 검증은 도메인 자원 존재 여부와 무관하다 — 인증 단계가 더 앞이므로 path 의 ID 가 999999 든 1 이든 401 이 먼저 나와야 한다. 따라서 dispatch 표는 fixture 시드 없이 그대로 호출하면 된다.

**JUnit5 명세 (의사 코드)**:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtectedEndpointAuthContractTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtFixture jwtFixture;

    record EndpointSpec(HttpMethod method, String path, String body, MediaType ct) {
        MockHttpServletRequestBuilder toRequestBuilder() { /* request 빌드 */ }
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void missingToken_returnsUnauthorized(EndpointSpec spec) throws Exception {
        mockMvc.perform(spec.toRequestBuilder())   // no Authorization
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void malformedToken_returnsInvalidToken(EndpointSpec spec) throws Exception {
        mockMvc.perform(spec.toRequestBuilder().header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void expiredToken_returnsInvalidToken(EndpointSpec spec) throws Exception {
        String jwt = jwtFixture.expired(seedUserA.id(), seedUserA.username());
        mockMvc.perform(spec.toRequestBuilder().header("Authorization", "Bearer " + jwt))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    static Stream<EndpointSpec> protectedEndpoints() {
        // 위 dispatch 표 그대로 20행
    }
}
```

**fixture 추가**:
- `JwtFixture.expired(Long uid, String username)` — secret 으로 서명 + `exp = Instant.now().minusSeconds(3600)` 인 JWT 생성.
- malformed 케이스는 fixture 없이 `"Bearer not.a.jwt"` 문자열 그대로 사용.

**REST Docs snippet**: `auth/protected-endpoint-missing-token`, `auth/protected-endpoint-malformed-token`, `auth/protected-endpoint-expired-token` (대표 1 endpoint 만 snippet 생성, 나머지 19 는 assertion 만).

### §1.6 검증 (`@Valid`) 응답 contract

- 표준 400:
  ```json
  { "success": false, "error": { "code": "VALIDATION_FAILED",
        "message": "필수 값이 누락되었거나 형식이 올바르지 않습니다." } }
  ```
- `MethodArgumentNotValidException` 의 모든 `FieldError` 가 응답에 노출되는지는 GlobalExceptionHandler 구현이 결정한다. 본 plan 은 **`error.code = "VALIDATION_FAILED"`** assertion 만 회귀 기준으로 채택 (필드별 메시지는 구현 디테일).

### §1.7 멀티파트 contract

- `POST /api/recordings`: `multipart/form-data`, part name **`audio`** (필수). 추가 query param: `scriptId?`, `sessionId?`, `stepId?`, `sessionSentenceId?`. 권장 포맷 16kHz mono PCM WAV.
- `POST /api/feedback/{feedbackId}/retry-word`: `multipart/form-data`, part name **`audio`** (필수).
- 잘못된 multipart (part name 오타, 파일 누락, content-type 위반, max-file-size 초과) → `INVALID_REQUEST` 400.

### §1.8 REST Docs snippet 명명 규칙

- 정상: `{kebab-controller}/{method}` — 예: `auth/signup`, `feedback/complete`, `session/list`.
- 시나리오 분기: `{kebab-controller}/{method}-{scenario-kebab}` — 예: `auth/signup-username-duplicated`, `auth/login-login-failed`.
- 인증 누락 공통: `{kebab-controller}/{method}-unauthorized`.
- 검증 실패: `{kebab-controller}/{method}-validation-failed`.
- snippet 종류: `request-fields`, `response-fields`, `path-parameters`, `request-parameters`, `request-headers`, `response-headers`. 에러 응답은 동일한 `response-fields` 사용 (envelope 의 `error` 만 다름).

---

## §2. 횡단 contract

### §2.1 응답 envelope (자기완결)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
record ApiResponse<T>(boolean success, T data, ApiError error) {
    record ApiError(String code, String message) {}
    static <T> ApiResponse<T> ok(T data);
    static ApiResponse<Void> ok();
    static <T> ApiResponse<T> fail(ErrorCode code);
    static <T> ApiResponse<T> fail(ErrorCode code, String message);
}
```

- 성공 응답 JSON (data 만 포함, error 키 omit):
  ```json
  { "success": true, "data": <T> }
  ```
- 실패 응답 JSON (error 만 포함, data 키 omit):
  ```json
  { "success": false, "error": { "code": "<ErrorCode>", "message": "<msg>" } }
  ```
- `data` 가 `Void` 인 경우 `data` 키 omit. `error.message` 는 ErrorCode 의 기본 메시지 (§2.2) 또는 호출 지점에서 보강된 문자열.
- Content-Type: `application/json; charset=UTF-8` (TTS 의 audio/mpeg 만 예외 — §3.26).

### §2.2 ErrorCode 카탈로그 (19개)

| code | HTTP | 기본 message |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | 요청 형식이 올바르지 않습니다. |
| `VALIDATION_FAILED` | 400 | 필수 값이 누락되었거나 형식이 올바르지 않습니다. |
| `UNAUTHORIZED` | 401 | 인증이 필요합니다. |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰입니다. |
| `LOGIN_FAILED` | 401 | 아이디 또는 비밀번호가 일치하지 않습니다. |
| `USERNAME_DUPLICATED` | 409 | 이미 사용 중인 아이디입니다. |
| `EMAIL_DUPLICATED` | 409 | 이미 사용 중인 이메일입니다. |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없습니다. |
| `TRACK_NOT_FOUND` | 404 | 학습 트랙을 찾을 수 없습니다. |
| `SCRIPT_NOT_FOUND` | 404 | 스크립트를 찾을 수 없습니다. |
| `STEP_NOT_FOUND` | 404 | 학습 단계를 찾을 수 없습니다. |
| `SESSION_NOT_FOUND` | 404 | 세션을 찾을 수 없습니다. |
| `SESSION_SENTENCE_NOT_FOUND` | 404 | 세션의 학습 문장을 찾을 수 없습니다. |
| `RECORDING_NOT_FOUND` | 404 | 녹음을 찾을 수 없습니다. |
| `FEEDBACK_NOT_FOUND` | 404 | 피드백을 찾을 수 없습니다. |
| `AUDIO_DECODE_FAILED` | 400 | 오디오 파일을 처리할 수 없습니다. |
| `MODEL_SERVER_UNAVAILABLE` | 503 | 모델 서버에 연결할 수 없습니다. |
| `MODEL_SERVER_ERROR` | 502 | 모델 서버 처리 중 오류가 발생했습니다. |
| `INTERNAL_ERROR` | 500 | 예기치 않은 오류가 발생했습니다. |

### §2.3 SecurityConfig 매핑

| 패턴 | 정책 |
| --- | --- |
| `/api/auth/**` | `permitAll` |
| `/api/health` | `permitAll` |
| `/error`, `/actuator/health` | `permitAll` |
| `/api/**` (위 외) | `authenticated` |

- 세션 정책: `STATELESS`.
- `JwtAuthFilter` 가 `UsernamePasswordAuthenticationFilter` 앞에 등록 → 헤더의 `Bearer` 검증 후 `SecurityContext` 에 `JwtPrincipal` 주입.
- 인증 실패 (보호 자원 + 토큰 없음/무효) → `JwtAuthEntryPoint` 가 401 + `ApiResponse.fail(UNAUTHORIZED)` 응답.
- `@CurrentUser` 어노테이션이 컨트롤러 파라미터에서 `JwtPrincipal` 주입 (id, username 접근 가능).

### §2.4 GlobalExceptionHandler 매핑

| 잡는 예외 | ErrorCode | HTTP |
| --- | --- | --- |
| `BusinessException` | e.code() | e.code().status() |
| `MethodArgumentNotValidException` | `VALIDATION_FAILED` | 400 |
| `MaxUploadSizeExceededException` | `INVALID_REQUEST` | 400 |
| `MissingServletRequestPartException` / `MissingServletRequestParameterException` | `INVALID_REQUEST` | 400 |
| `AuthenticationException` / `AccessDeniedException` | `UNAUTHORIZED` | 401 |
| `Exception` (catch-all) | `INTERNAL_ERROR` | 500 |

응답은 모두 `ApiResponse.fail(...)` 형식의 JSON.

---

## §3. Endpoint 별 contract 명세 (Tier 1, 26개)

### 그룹 A — Health / Auth (6 endpoint)

#### 1. `GET /api/health` → HealthController.health

- **인증**: `permitAll`.
- **Test class**: `app.HealthControllerTest`.
- **Snippet base**: `health/check`.

##### Request
- 없음.

##### Response (golden 200)
`ApiResponse<Map<String, Object>>`:
```json
{ "success": true, "data": {
    "status": "UP",
    "service": "echo-app-backend",
    "timestamp": "2026-05-10T12:34:56Z"
} }
```
- Content-Type `application/json; charset=UTF-8`. envelope 사용 (data 는 임의의 Map).
- `data.status`: 항상 `"UP"`. `data.service`: 항상 `"echo-app-backend"`. `data.timestamp`: ISO 8601 UTC (`Instant.now().toString()`).

##### 시나리오
- **golden** — 요청 → 200 + `apiSuccess()` + `data.status="UP"` + `data.service="echo-app-backend"` + `data.timestamp` 가 ISO 8601 형식.

##### Mock 정책
- 없음.

---

#### 2. `POST /api/auth/signup` → AuthController.signup

- **인증**: `permitAll`.
- **Test class**: `auth.AuthControllerTest#signup`.
- **Snippet base**: `auth/signup`.

##### Request
`SignupRequest`:

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `username` | String | `@NotBlank`, `@Pattern("^[a-zA-Z0-9_]{3,30}$")` |
| `password` | String | `@NotBlank`, `@Size(min=6, max=50)` |
| `nickname` | String | `@NotBlank`, `@Size(max=30)` |
| `email` | String | `@NotBlank`, `@Email` |
| `agreedTerms` | boolean | `@AssertTrue` |

```json
{ "username":"alice", "password":"Pa$$w0rd!", "nickname":"Alice", "email":"alice@test.com", "agreedTerms": true }
```

##### Response (golden 201)
`ApiResponse<TokenResponse>`:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "tokenType": "Bearer",
    "expiresInSec": 3600,
    "user": {
      "id": 1, "username":"alice", "email":"alice@test.com", "nickname":"Alice",
      "streak": 0, "exp": 0, "createdAt": "2026-05-10T12:00:00Z"
    }
  }
}
```
- HTTP status **201 Created**.

##### 시나리오
- **golden** — 빈 DB → POST → 201 + `success=true` + `data.accessToken` 가 비-empty + `data.user.username="alice"` + `data.user.streak=0` + `data.user.exp=0`.
- **VALIDATION_FAILED** — `username` 길이 2 (Pattern 위반) 또는 `agreedTerms=false` → 400 + `error.code="VALIDATION_FAILED"`.
- **USERNAME_DUPLICATED** — `UserFixture.seedUserA()` 가 미리 `username=alice` persist → 같은 `username` 으로 POST → 409 + `error.code="USERNAME_DUPLICATED"`.
- **EMAIL_DUPLICATED** — 시드 사용자와 같은 `email`, 다른 `username` 으로 POST → 409 + `error.code="EMAIL_DUPLICATED"`.

##### Mock 정책
- 없음.

---

#### 3. `POST /api/auth/login` → AuthController.login

- **인증**: `permitAll`.
- **Test class**: `auth.AuthControllerTest#login`.
- **Snippet base**: `auth/login`.

##### Request
`LoginRequest`: `{ "username":"alice", "password":"Pa$$w0rd!" }`. 두 필드 모두 `@NotBlank`.

##### Response (golden 200)
`ApiResponse<TokenResponse>` (#2 와 동일 schema).

##### 시나리오
- **golden** — `UserFixture.seedUserA()` (BCrypt 로 `Pa$$w0rd!` 해시 저장) → POST 정상 자격 → 200 + `data.accessToken` 비-empty + `data.user.id=시드ID`.
- **VALIDATION_FAILED** — `password` 빈 문자열 → 400 + `VALIDATION_FAILED`.
- **LOGIN_FAILED** — 시드 사용자 존재, 잘못된 password 또는 존재하지 않는 username → 401 + `error.code="LOGIN_FAILED"`. (사용자 존재/미존재 구분 노출 안 함.)

##### Mock 정책
- 없음.

---

#### 4. `POST /api/auth/check-username` → AuthController.checkUsername

- **인증**: `permitAll`.
- **Test class**: `auth.AuthControllerTest#checkUsername`.
- **Snippet base**: `auth/check-username`.

##### Request
`CheckRequest`: `{ "value": "alice" }`. `value @NotBlank`.

##### Response (golden 200)
`ApiResponse<CheckResponse>`:
```json
{ "success": true, "data": { "available": true } }
```

##### 시나리오
- **golden (available)** — 빈 DB + `{"value":"alice"}` → 200 + `data.available=true`.
- **golden (unavailable)** — `seedUserA()` 후 `{"value":"alice"}` → 200 + `data.available=false`.
- **VALIDATION_FAILED** — `{"value":""}` → 400 + `VALIDATION_FAILED`.

##### Mock 정책
- 없음.

---

#### 5. `POST /api/auth/check-email` → AuthController.checkEmail

- **인증**: `permitAll`.
- **Test class**: `auth.AuthControllerTest#checkEmail`.
- **Snippet base**: `auth/check-email`.

##### Request
`CheckRequest`: `{ "value": "alice@test.com" }`.

##### Response (golden 200)
`ApiResponse<CheckResponse>` (#4 와 동일).

##### 시나리오
- **golden (available)** — 빈 DB → 200 + `data.available=true`.
- **golden (unavailable)** — `seedUserA()` (email=alice@test.com) → 200 + `data.available=false`.
- **VALIDATION_FAILED** — `{"value":""}` → 400 + `VALIDATION_FAILED`.

##### Mock 정책
- 없음.

---

#### 6. `GET /api/auth/oauth2/google/demo` → AuthController.demoGoogleLogin

- **인증**: `permitAll`.
- **Test class**: `auth.AuthControllerTest#demoGoogleLogin`.
- **Snippet base**: `auth/oauth2-google-demo`.

##### Request
- 없음.

##### Response (golden 200)
`ApiResponse<TokenResponse>` (#2 와 동일 schema).
- 데모 사용자 (yaml 또는 코드 상수 — 본 plan 에서는 username `demo_google`) 가 자동 upsert 되어 그 사용자의 토큰을 반환.

##### 시나리오
- **golden (첫 호출, upsert)** — 빈 DB → GET → 200 + `data.user.username="demo_google"` + `data.accessToken` 비-empty. DB 에 1명 persist 확인.
- **golden (재호출, 멱등)** — 한 번 더 GET → 200 + 같은 `data.user.id`. DB 에 여전히 1명.

##### Mock 정책
- 없음.

---

### 그룹 B — Member (2 endpoint)

#### 7. `GET /api/members/me` → MemberController.me

- **인증**: `authenticated`.
- **Test class**: `member.MemberControllerTest#me`.
- **Snippet base**: `member/me`.

##### Request
- Header `Authorization: Bearer <jwt>`. body 없음.

##### Response (golden 200)
`ApiResponse<UserResponse>`:
```json
{
  "success": true,
  "data": {
    "id": 1, "username": "alice", "email": "alice@test.com", "nickname": "Alice",
    "streak": 0, "exp": 0, "createdAt": "2026-05-10T12:00:00Z"
  }
}
```

##### 시나리오
- **golden** — `seedUserA()` → `bearer(userA)` → GET → 200 + `data.username="alice"` + 모든 필드 존재.
- **unauthorized** — header 미포함 → 401 + `error.code="UNAUTHORIZED"`.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **USER_NOT_FOUND** — `bearer(userId=999999)` (존재하지 않는 ID 의 토큰 직접 발급) → 404 + `error.code="USER_NOT_FOUND"`. (실 운영에서 거의 없는 케이스지만 contract 로 노출.)

##### Mock 정책
- 없음.

---

#### 8. `PATCH /api/members/me/nickname` → MemberController.changeNickname

- **인증**: `authenticated`.
- **Test class**: `member.MemberControllerTest#changeNickname`.
- **Snippet base**: `member/change-nickname`.

##### Request
`UpdateNicknameRequest`:

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `nickname` | String | `@NotBlank`, `@Size(max=30)` |

```json
{ "nickname": "Newbie" }
```

##### Response (golden 200)
`ApiResponse<UserResponse>` (#7 과 동일 schema, `nickname` 만 갱신):
```json
{ "success": true, "data": { "id":1, "username":"alice", ..., "nickname":"Newbie", ... } }
```

##### 시나리오
- **golden** — `seedUserA()` → `bearer(userA)` → PATCH `{"nickname":"Newbie"}` → 200 + `data.nickname="Newbie"` + DB 의 user.nickname 도 변경.
- **unauthorized** — header 미포함 → 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **VALIDATION_FAILED** — `{"nickname":""}` 또는 31자 이상 → 400 + `VALIDATION_FAILED`.
- **USER_NOT_FOUND** — `bearer(999999)` → 404 + `USER_NOT_FOUND`.

##### Mock 정책
- 없음.

---

### 그룹 C — Learning content (4 endpoint)

#### 9. `GET /api/tracks` → TrackController.list

- **인증**: `authenticated`.
- **Test class**: `learning.TrackControllerTest#list`.
- **Snippet base**: `track/list`.

##### Request
- 없음 (header 만).

##### Response (golden 200)
`ApiResponse<List<TrackSummaryResponse>>`:
```json
{ "success": true, "data": [
    { "id":1, "title":"Beginner Conversations", "description":"...", "displayOrder":1, "chapterCount": 5 },
    { "id":2, "title":"Travel English",          "description":"...", "displayOrder":2, "chapterCount": 8 }
] }
```

##### 시나리오
- **golden (비-empty)** — TrackFixture.seedTracks(2) → 200 + `data.length=2` + 각 항목의 모든 필드 존재 + `displayOrder` 오름차순.
- **golden (empty)** — 빈 DB → 200 + `data=[]`.
- **unauthorized** — header 미포함 → 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).

##### Mock 정책
- 없음.

---

#### 10. `GET /api/tracks/{trackId}` → TrackController.detail

- **인증**: `authenticated`.
- **Test class**: `learning.TrackControllerTest#detail`.
- **Snippet base**: `track/detail`.

##### Request
- Path: `trackId` (Long).

##### Response (golden 200)
`ApiResponse<TrackDetailResponse>`:
```json
{ "success": true, "data": {
    "id":1, "title":"Beginner Conversations", "description":"...", "displayOrder":1,
    "chapters": [
        { "scriptId":11, "chapterOrder":1, "title":"At the Cafe", "difficulty":"BEGINNER" },
        { "scriptId":12, "chapterOrder":2, "title":"Ordering Food", "difficulty":"BEGINNER" }
    ]
} }
```

##### 시나리오
- **golden** — TrackFixture.seedTrackWithChapters(2) → GET `/api/tracks/{id}` → 200 + `data.chapters.length=2` + `chapterOrder` 오름차순.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **TRACK_NOT_FOUND** — GET `/api/tracks/999999` → 404 + `error.code="TRACK_NOT_FOUND"`.

##### Mock 정책
- 없음.

---

#### 11. `GET /api/scripts/recommended/today` → ScriptController.recommendedToday

- **인증**: `authenticated`.
- **Test class**: `learning.ScriptControllerTest#recommendedToday`.
- **Snippet base**: `script/recommended-today`.

##### Request
- 없음 (header 만).

##### Response (golden 200)
`ApiResponse<List<ScriptSummaryResponse>>`:
```json
{ "success": true, "data": [
    { "id":21, "title":"Greetings", "difficulty":"BEGINNER", "isPreset": true },
    { "id":22, "title":"Numbers",   "difficulty":"BEGINNER", "isPreset": true }
] }
```

##### 시나리오
- **golden (비-empty)** — ScriptFixture.seedPresetScripts(N=2) → 200 + `data.length>=1` + 모두 `isPreset=true`.
- **golden (empty)** — preset 스크립트 0건 → 200 + `data=[]`.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).

##### Mock 정책
- 없음 (날짜 기반 결정적 셔플은 service 책임이라 mock 불필요).

---

#### 12. `GET /api/scripts/{scriptId}` → ScriptController.detail

- **인증**: `authenticated`.
- **Test class**: `learning.ScriptControllerTest#detail`.
- **Snippet base**: `script/detail`.

##### Request
- Path: `scriptId` (Long).

##### Response (golden 200)
`ApiResponse<ScriptDetailResponse>`:
```json
{ "success": true, "data": {
    "id":21, "title":"Greetings", "content":"Hello! How are you?", "difficulty":"BEGINNER", "isPreset": true,
    "steps": [
        { "id":201, "orderIndex":0, "kind":"INTRO",  "prompt":"Listen first." },
        { "id":202, "orderIndex":1, "kind":"RECORD", "prompt":"Now record.",   "targetText":"Hello!" }
    ]
} }
```
- `targetText`: `INTRO` 단계는 응답에서 키 자체가 **omit** 된다 (`@JsonInclude(NON_NULL)`, `string?`, REFINED §5.9). `RECORD` 단계는 키 존재 + 비-empty. **회귀 기준 = `INTRO` 의 `targetText` `doesNotExist` + `RECORD` 의 `targetText` 비-empty**.

##### 시나리오
- **golden** — ScriptFixture.seedScriptWithSteps(intro=1, record=2) → GET → 200 + `data.steps.length=3` + `jsonPath('$.data.steps[?(@.kind=="INTRO")].targetText').doesNotExist()` + `jsonPath('$.data.steps[?(@.kind=="RECORD")].targetText')` 비-empty.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **SCRIPT_NOT_FOUND** — GET `/api/scripts/999999` → 404 + `error.code="SCRIPT_NOT_FOUND"`.

##### Mock 정책
- 없음.

---

### 그룹 D — Session (5 endpoint)

#### 13. `GET /api/sessions` → SessionController.list

- **인증**: `authenticated`.
- **Test class**: `learning.SessionControllerTest#list`.
- **Snippet base**: `session/list`.

##### Request
- 없음 (header 만).

##### Response (golden 200)
`ApiResponse<List<SessionResponse>>`:
```json
{ "success": true, "data": [
    { "id":31, "title":"My Cafe", "scriptText":"Hello!", "favorite":true,
      "sentences":[ {"id":301,"sentenceIndex":0,"text":"Hello!"} ],
      "createdAt":"2026-05-10T10:00:00Z", "updatedAt":"2026-05-10T11:00:00Z" }
] }
```

##### 시나리오
- **golden (비-empty)** — `seedUserA()` + `SessionFixture.seedSession(userA, "My Cafe", "Hello!")` → `bearer(userA)` → 200 + `data.length=1` + `data[0].sentences.length=1`.
- **golden (empty)** — `seedUserA()` 만 (세션 0건) → 200 + `data=[]`.
- **golden (cross-user 격리)** — `seedUserA()` + `seedUserB()` + B의 세션 1건 → A 토큰으로 GET → 200 + `data=[]` (A 의 세션만 반환).
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).

##### Mock 정책
- 없음.

---

#### 14. `POST /api/sessions` → SessionController.create

- **인증**: `authenticated`.
- **Test class**: `learning.SessionControllerTest#create`.
- **Snippet base**: `session/create`.

##### Request
`SessionCreateRequest`:

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `title` | String | `@NotBlank`, `@Size(max=100)` |

```json
{ "title": "My Coffee Order" }
```

> `scriptText` 갱신과 sentence split 은 `PATCH /api/sessions/{sessionId}` (#16, `SessionUpdateRequest.scriptText`) 에서만 가능. POST 는 `title` 만 받고 빈 세션을 생성한다.

##### Response (golden 201)
`ApiResponse<SessionResponse>` — 빈 세션 (sentences=[], scriptText="" 또는 null, favorite=false):
```json
{ "success": true, "data": {
    "id": 31, "title": "My Coffee Order",
    "scriptText": "", "favorite": false, "sentences": [],
    "createdAt": "2026-05-10T10:00:00Z", "updatedAt": "2026-05-10T10:00:00Z"
} }
```

##### 시나리오
- **golden** — `seedUserA()` + bearer + POST `{"title":"My Coffee Order"}` → 201 + `data.title="My Coffee Order"` + `data.sentences=[]` + `data.scriptText=""` (또는 null) + `data.favorite=false`. (scriptText 와 sentences 갱신은 PATCH 시나리오 #16 참조.)
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **VALIDATION_FAILED** — `{"title":""}` 또는 100자 초과 → 400 + `VALIDATION_FAILED`.

##### Mock 정책
- 없음.

---

#### 15. `GET /api/sessions/{sessionId}` → SessionController.detail

- **인증**: `authenticated`.
- **Test class**: `learning.SessionControllerTest#detail`.
- **Snippet base**: `session/detail`.

##### Request
- Path: `sessionId` (Long).

##### Response (golden 200)
`ApiResponse<SessionResponse>` (#13 과 동일).

##### 시나리오
- **golden** — `seedUserA()` + 본인 세션 → bearer + GET → 200 + `data.id=세션ID`.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **SESSION_NOT_FOUND (cross-user)** — `seedUserA() + seedUserB()` + B 의 세션 → A 토큰으로 GET → 404 + `error.code="SESSION_NOT_FOUND"`. (cross-user 도 not-found 로 통합 — 존재 노출 방지.)
- **SESSION_NOT_FOUND (not-exists)** — GET `/api/sessions/999999` → 404 + `SESSION_NOT_FOUND`.

##### Mock 정책
- 없음.

---

#### 16. `PATCH /api/sessions/{sessionId}` → SessionController.patch

- **인증**: `authenticated`.
- **Test class**: `learning.SessionControllerTest#patch`.
- **Snippet base**: `session/patch`.

##### Request
`SessionUpdateRequest`:

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `title` | String? | `@Size(max=100)`, null 허용 (변경 없음) |
| `scriptText` | String? | `@Size(max=5000)`, null 허용 (변경 없음) |
| `favorite` | Boolean? | null 허용 (토글 없음) |

```json
{ "title": "Renamed", "favorite": true }
```

##### Response (golden 200)
`ApiResponse<SessionResponse>` (반영 후 상태).

##### 시나리오
- **golden (title 만)** — 본인 세션 + `{"title":"Renamed"}` → 200 + `data.title="Renamed"` + `data.scriptText` / `data.favorite` 보존.
- **golden (favorite 토글)** — `{"favorite":true}` → 200 + `data.favorite=true` + 다른 필드 보존.
- **golden (scriptText 갱신)** — `{"scriptText":"New text. New sentence."}` → 200 + `data.sentences.length=2` (재-split) + 기존 sentence ID 모두 새 ID 로 교체 (orphanRemoval).
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **VALIDATION_FAILED** — `{"title":""}` (NotBlank 가 아니라 Size 만 있어도 빈 문자열은 명시적으로 블랭크 처리 — VALIDATION_FAILED 가 안 떨어지면 본 시나리오는 omit) — 구현 확인 후 적용. 본 plan 에서는 검증 시나리오 한 줄만 명시.
- **SESSION_NOT_FOUND** — cross-user 또는 not-exists → 404.

##### Mock 정책
- 없음.

---

#### 17. `DELETE /api/sessions/{sessionId}` → SessionController.delete

- **인증**: `authenticated`.
- **Test class**: `learning.SessionControllerTest#delete`.
- **Snippet base**: `session/delete`.

##### Request
- Path: `sessionId`. body 없음.

##### Response (golden 200)
- 본문: `ApiResponse<Void>` 형태 `{ "success": true }` (data / error 키 모두 omit, `@JsonInclude(NON_NULL)`). HTTP status 200, Content-Type `application/json; charset=UTF-8`. REFINED §1.1 의 void 작업 contract — 204 + 빈 body 반환은 회귀 위반.

##### 시나리오
- **golden** — 본인 세션 → DELETE → 200 + `jsonPath('$.success').value(true)` + `jsonPath('$.data').doesNotExist()` + `jsonPath('$.error').doesNotExist()` + DB 에 row 없음.
- **golden (cascade SET NULL)** — 본인 세션 + 그 세션을 참조하는 Recording 1건 시드 → DELETE → 200 + 동일 envelope assertion + Recording.session_id 가 NULL, 본문 (target_text_snapshot) 보존.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **SESSION_NOT_FOUND** — cross-user 또는 not-exists → 404.

##### Mock 정책
- 없음.

---

### 그룹 E — Recording (1 endpoint, 멀티파트)

#### 18. `POST /api/recordings` → RecordingController.upload

- **인증**: `authenticated`.
- **Test class**: `recording.RecordingControllerTest#upload`.
- **Snippet base**: `recording/upload`.

##### Request
- `multipart/form-data`, part name **`audio`** (필수, WAV).
- Query parameter (모드 결정):

| 모드 | 필요한 query |
| --- | --- |
| script-flow | `scriptId` + `stepId` |
| session-sentence | `sessionId` + `sessionSentenceId` |
| session-free-form | `sessionId` (only) |

- 잘못된 조합 (예: scriptId + sessionId 동시, 4 컨텍스트 모두 누락 등) → `INVALID_REQUEST` (400).
- **strict 규정 노트**: REFINED §4.6 는 "위 세 조합 외의 케이스는 서버가 정상 응답을 보장하지 않습니다" 로 약화 표현이지만, 본 plan 은 회귀 기준을 명확히 하기 위해 mode 위반 시 **400 INVALID_REQUEST** 를 응답해야 회귀 없음으로 strict 하게 규정한다. 새 backend 구현이 다른 status (예: 500) 또는 다른 ErrorCode 로 응답하면 본 plan 의 회귀 기준 위반.
- **cross-tenant 정책**: 타 사용자 소유 ID 사용은 user 스코프 조회의 자연 결과로 `*_NOT_FOUND` (404) 가 반환된다 (REFINED §4.6).

##### Response (golden 201, mode 별)

`ApiResponse<RecordingResponse>`. mode 에 따라 context 키 (`scriptId` / `sessionId` / `stepId` / `sessionSentenceId`) 가 nullable. `@JsonInclude(NON_NULL)` 정책으로 **해당 모드에 사용되지 않은 컨텍스트 키는 응답에서 omit** (REFINED §4.6 / §5.3). 골든 assertion 은 mode 별로 사용 컨텍스트 키 존재 + 미사용 컨텍스트 키 부재 (`doesNotExist`) 둘 다 검증.

**script-flow** (`scriptId`+`stepId` 만):
```json
{ "success": true, "data": {
    "id":501, "scriptId":21, "stepId":202,
    "durationSec": 1.05,
    "perceived": ["w","ʌ","t","ɚ"],
    "canonical": ["w","ɔ","t","ɚ"],
    "peakSoftmax": [0.91, 0.62, 0.88, 0.79],
    "stepScore": 78.0,
    "guidanceKr": "ɔ 모음을 더 둥글게.",
    "errors": [{"op":"substitution","canonical":"ɔ","perceived":"ʌ","canonicalIndex":1}],
    "wrongWords": [{"word":"water","index":0}],
    "createdAt": "2026-05-10T12:00:00Z"
} }
```

**session-sentence** (`sessionId`+`sessionSentenceId`):
```json
{ "success": true, "data": {
    "id":502, "sessionId":31, "sessionSentenceId":301,
    "durationSec": 2.10,
    "perceived": [...], "canonical": [...], "peakSoftmax": [...],
    "stepScore": 92.5, "guidanceKr": "...", "errors": [], "wrongWords": [],
    "createdAt": "..."
} }
```

**session-free-form** (`sessionId` only):
```json
{ "success": true, "data": {
    "id":503, "sessionId":31,
    "durationSec": 8.30,
    "perceived":[...], "canonical":[...], "peakSoftmax":[...],
    "stepScore": 84.7, "guidanceKr":"...", "errors": [], "wrongWords": [],
    "createdAt":"..."
} }
```

##### 시나리오 (mode 별 golden + 모든 ErrorCode)

- **golden script-flow** — `seedUserA()` + ScriptFixture.seedScriptWithSteps + `WavFixtures.WATER_1S` + mock `modelServerClient.g2p(...)` returns `["w","ɔ","t","ɚ"]` + mock `analyze(...)` returns `AnalyzeMockResponses.WATER_WITH_ERROR` + mock `llmClient.summarizeRecording(...)` returns `LlmMockResponses.GUIDANCE_SIMPLE` → POST multipart → 201 + `data.scriptId` / `data.stepId` 반환 + `data.sessionId` 키 omit + `data.wrongWords.length=1` + `data.errors.length=1`.
- **golden session-sentence** — `seedUserA()` + SessionFixture.seedSession (sentence 2개) + WAV + mock 응답 → POST → 201 + `data.sessionId` / `data.sessionSentenceId` 반환 + script 키 omit.
- **golden session-free-form** — `sessionId` 만 → 201 + `data.sessionId` 만 + `data.sessionSentenceId` 키 omit.
- **golden (perfect, errors empty)** — mock `analyze` returns `WATER_PERFECT` (errors=[]) + mock `llm` returns `GUIDANCE_PERFECT` → 201 + `data.errors=[]` + `data.wrongWords=[]`.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **INVALID_REQUEST (mode 위반)** — 다음 케이스 각각 → 400 + `error.code="INVALID_REQUEST"` (본 plan 의 strict 규정):
  - **두 mode 혼합**: `scriptId` + `sessionId` 동시.
  - **컨텍스트 모두 누락**: 4 컨텍스트 query 모두 미지정 (mode 결정 불가).
  - **불완전 script-flow**: `scriptId` 만 (`stepId` 없음) — 3 canonical mode 어디에도 해당 안 됨.
  - **불완전 session-sentence**: `sessionSentenceId` 만 (`sessionId` 없음).
- **INVALID_REQUEST (multipart part 누락)** — multipart 보내되 part name 이 `audio` 가 아니라 `file` → 400 + `INVALID_REQUEST`.
- **AUDIO_DECODE_FAILED** — multipart `audio` 가 빈 byte[] 또는 깨진 헤더 → 400 + `AUDIO_DECODE_FAILED`.
- **SCRIPT_NOT_FOUND** — `scriptId=999999` + `stepId=...` → 404 + `SCRIPT_NOT_FOUND`.
- **STEP_NOT_FOUND** — script-flow + 존재하는 scriptId + 존재하지 않는 stepId → 404 + `STEP_NOT_FOUND`.
- **SESSION_NOT_FOUND** — `sessionId=999999` (또는 cross-user) + `sessionSentenceId=...` → 404 + `SESSION_NOT_FOUND`.
- **SESSION_SENTENCE_NOT_FOUND** — 존재하는 sessionId + 존재하지 않는 (또는 다른 세션의) sessionSentenceId → 404 + `SESSION_SENTENCE_NOT_FOUND`.
- **MODEL_SERVER_UNAVAILABLE** — mock `modelServerClient.analyze(...)` throws `BusinessException(MODEL_SERVER_UNAVAILABLE, "...")` → 503 + `error.code="MODEL_SERVER_UNAVAILABLE"`.
- **MODEL_SERVER_ERROR** — mock 이 throws `BusinessException(MODEL_SERVER_ERROR, "...")` → 502 + `error.code="MODEL_SERVER_ERROR"`.

##### Mock 정책
- `modelServerClient.g2p(targetText)`: script-flow / session-sentence 모드에서만 호출됨. `Mockito.when(modelServerClient.g2p(any())).thenReturn(["w","ɔ","t","ɚ"])`.
- `modelServerClient.analyze(byte[], String)`: 항상 호출. `WATER_WITH_ERROR` (1초 발음 + ɔ→ʌ 오류 1건) 가 기본 stub.
- `llmClient.summarizeRecording(...)`: 항상 호출. `GUIDANCE_SIMPLE` (`guidanceKr` + `wrongWords=[{word:water,index:0}]`) 또는 `GUIDANCE_PERFECT` (빈 wrongWords).

---

### 그룹 F — Feedback (5 endpoint)

#### 19. `POST /api/feedback/generate` → FeedbackController.generate

- **인증**: `authenticated`.
- **Test class**: `feedback.FeedbackControllerTest#generate`.
- **Snippet base**: `feedback/generate`.

##### Request
`GenerateFeedbackRequest`:

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `scriptId` | Long? | scriptId XOR sessionId (정확히 하나) |
| `sessionId` | Long? | scriptId XOR sessionId |
| `recordingIds` | List<Long> | `@NotEmpty` |

```json
{ "scriptId": 21, "sessionId": null, "recordingIds": [501, 502, 503] }
```

##### Response (golden 200)
`ApiResponse<FeedbackResponse>`:
```json
{ "success": true, "data": {
    "id": 701, "scriptId": 21, "sessionId": null,
    "title": "Greetings",
    "accuracy": 86.5,
    "weakPhoneme": "ɔ",
    "practiceWord": "water",
    "guidanceKr": "ɔ 발음을 더 둥글게.",
    "errors": [
        {"op":"substitution","canonical":"ɔ","perceived":"ʌ","canonicalIndex":1}
    ],
    "createdAt": "2026-05-10T12:30:00Z"
} }
```

**perfect 케이스** (모든 녹음 errors=[], accuracy=100):
```json
{ "success": true, "data": {
    "id": 702, "scriptId": 21,
    "title": "Greetings",
    "accuracy": 100.0,
    "practiceWord": "rabbit",
    "guidanceKr": "전반적으로 안정적인 발음이지만 더 연습하면 좋아요.",
    "errors": [],
    "createdAt": "2026-05-10T12:35:00Z"
} }
```
- `weakPhoneme` 키 **omit** (`@JsonInclude(NON_NULL)` + REFINED §5.4 `string?`).
- `practiceWord` / `guidanceKr` 는 perfect 케이스에서도 **항상 비-empty** (REFINED §5.4 의 fallback 체인 contract — 시드 챕터 단어 → LLM 추천 → 음소 매핑 → yaml `app.feedback.default-practice-word` 순). fallback 체인이 끊겨 둘 중 하나가 null/empty 면 본 contract 위반.

##### 시나리오
- **golden script-flow** — `seedUserA()` + ScriptFixture.seedScriptWithSteps + RecordingFixture.seedScriptFlowRecording ×3 → POST → 200 + `data.scriptId` 반환 + `jsonPath('$.data.sessionId').doesNotExist()` + `data.weakPhoneme="ɔ"` (키 존재) + `data.practiceWord` 비-empty (REFINED §5.4 non-null contract) + `data.guidanceKr` 비-empty (동일 contract) + `data.errors.length>=1`.
- **golden session-flow** — sessionFixture + sessionFlowRecording ×3 → POST `{"sessionId":31, "recordingIds":[...]}` → 200 + `data.sessionId` 반환 + `data.scriptId` 키 omit.
- **golden (perfect, weakPhoneme omit)** — 모든 recording 의 errors=[] → 200 + `jsonPath('$.data.weakPhoneme').doesNotExist()` + `jsonPath('$.data.practiceWord')` 비-empty + `jsonPath('$.data.guidanceKr')` 비-empty + `data.accuracy=100.0` (REFINED §5.4 non-null fallback 체인 contract).
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **INVALID_REQUEST (XOR 위반)** — `scriptId` + `sessionId` 둘 다 set 또는 둘 다 null → 400 + `INVALID_REQUEST`.
- **VALIDATION_FAILED** — `{"recordingIds":[]}` → 400 + `VALIDATION_FAILED`.
- **SCRIPT_NOT_FOUND** — `scriptId=999999` → 404 + `SCRIPT_NOT_FOUND`.
- **SESSION_NOT_FOUND** — `sessionId=999999` 또는 cross-user → 404 + `SESSION_NOT_FOUND`.
- **RECORDING_NOT_FOUND (cross-user)** — `recordingIds` 에 user B 의 recording ID 포함 → 404 + `RECORDING_NOT_FOUND`.
- **RECORDING_NOT_FOUND (cross-context)** — `scriptId=21` 인데 `recordingIds` 에 session-flow recording 포함 → 404 + `RECORDING_NOT_FOUND`.
- **MODEL_SERVER_UNAVAILABLE** — golden 사전 셋업 + 모델 서버 의존 컴포넌트가 `BusinessException(MODEL_SERVER_UNAVAILABLE, ...)` throw → 503 + `error.code="MODEL_SERVER_UNAVAILABLE"` (REFINED §4.4 의 generate 주요 에러 목록).
- **MODEL_SERVER_ERROR** — 모델 서버 의존 컴포넌트가 `BusinessException(MODEL_SERVER_ERROR, ...)` throw → 502 + `error.code="MODEL_SERVER_ERROR"`.

##### Mock 정책
- 새 구현이 LLM 또는 모델 서버 의존을 사용한다면, 그 의존을 `@MockBean` 으로 교체하고 정상 stub 또는 `BusinessException` throw 로 케이스 분기. 본 plan 은 구체 클래스 이름을 강제하지 않는다 — REFINED §4.4 의 ErrorCode 가 응답에 노출되는지가 회귀 기준.
- `MODEL_SERVER_UNAVAILABLE` / `MODEL_SERVER_ERROR` 시나리오는 그 의존이 throw 하는 케이스를 mock 으로 강제해 트리거.
- 정상 (golden) 케이스에서는 LLM 의존이 비-empty `guidanceKr` / `weakPhoneme` 등을 반환하도록 stub.

---

#### 20. `POST /api/feedback/{feedbackId}/retry-word` → FeedbackController.retryWord

- **인증**: `authenticated`.
- **Test class**: `feedback.FeedbackControllerTest#retryWord`.
- **Snippet base**: `feedback/retry-word`.

##### Request
- Path: `feedbackId` (Long).
- `multipart/form-data`, part name **`audio`** (필수).

##### Response (golden 200)
`ApiResponse<RetryWordResponse>`:
```json
{ "success": true, "data": {
    "correct": true,
    "perceived": ["w","ɔ","t","ɚ"],
    "canonical": ["w","ɔ","t","ɚ"],
    "score": 88.0,
    "guidanceKr": "이번엔 더 가까워졌어요."
} }
```
- `record RetryWordResponse(boolean correct, List<String> perceived, List<String> canonical, double score, String guidanceKr)` — 정확히 5 필드. `word` / `stepScore` 키 없음 (저장 없음, read-only retry).
- `correct`: `perceived == canonical` 일 때 true. `score`: 0~100. `guidanceKr`: 한 줄 한국어 가이드.

##### 시나리오
- **golden (correct=true)** — 본인 feedback (practiceWord=water) + `WATER_1S` WAV + mock `g2p` returns `["w","ɔ","t","ɚ"]` + mock `analyze` returns `AnalyzeMockResponses.WATER_PERFECT` + mock `llmClient.retryGuidance(...)` returns `"이번엔 더 가까워졌어요."` → 200 + `data.correct=true` + `data.perceived == data.canonical` + `data.score >= 80.0` + `data.guidanceKr` 비-empty + `data` 키에 `word`/`stepScore` 부재.
- **golden (correct=false)** — 같은 사전 셋업 + mock `analyze` returns `AnalyzeMockResponses.WATER_WITH_ERROR` (perceived 의 ɔ → ʌ) → 200 + `data.correct=false` + `data.perceived[1]="ʌ"` + `data.canonical[1]="ɔ"` + `data.score < 80.0`.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **FEEDBACK_NOT_FOUND** — feedbackId=999999 또는 cross-user → 404 + `FEEDBACK_NOT_FOUND`.
- **AUDIO_DECODE_FAILED** — 깨진 audio (헤더 없는 byte[]) → 400 + `AUDIO_DECODE_FAILED`.
- **INVALID_REQUEST (multipart 위반)** — bearer + 다음 세 케이스 각각 → 400 + `error.code="INVALID_REQUEST"` (REFINED §4.4 의 retry-word 주요 에러 목록 — "빈 파트/사이즈 초과"):
  - **missing part**: multipart 본문에 `audio` part 없음 (다른 part 만 또는 part 0개).
  - **wrong part name**: part name 이 `audio` 가 아닌 `file` / `recording` 등.
  - **size 초과**: `application-test.yaml` 의 `spring.servlet.multipart.max-file-size` (10MB) 초과 byte[]. 큰 파일을 실제 만드는 대신 `MaxUploadSizeExceededException` 강제 mock 으로 시뮬레이션 가능.
- **MODEL_SERVER_UNAVAILABLE** — mock 이 throws → 503 + `MODEL_SERVER_UNAVAILABLE`.
- **MODEL_SERVER_ERROR** — mock 이 throws → 502 + `MODEL_SERVER_ERROR`.

##### Mock 정책
- 새 구현이 모델 서버 / LLM 의존을 사용한다면 `@MockBean` 으로 교체. golden 은 정상 stub, MODEL_SERVER_* 시나리오는 의존이 `BusinessException` throw 하도록 강제. INVALID_REQUEST 의 multipart 위반은 mock 없이 `MockMvcRequestBuilders.multipart(...)` 로 직접 잘못된 multipart 요청 빌드.

---

#### 21. `POST /api/feedback/{feedbackId}/complete` → FeedbackController.complete

- **인증**: `authenticated`.
- **Test class**: `feedback.FeedbackControllerTest#complete`.
- **Snippet base**: `feedback/complete`.

##### Request
- Path: `feedbackId`. body 없음.

##### Response (golden 200)
`ApiResponse<UserResponse>` (보상 가산 후 사용자 스냅샷):
```json
{ "success": true, "data": {
    "id":1, "username":"alice", ..., "streak": 1, "exp": 10, ...
} }
```
- 가산 정책: `app.feedback.completionExp` (기본 10) 만큼 exp 증가, streak 정책 (어제 학습 +1, 그 외 1 리셋) 적용. KST 기준.

##### 시나리오
- **golden (1차 호출, 가산)** — 본인 feedback (completed=false) + `seedUserA()` (exp=0, streak=0) → POST → 200 + `data.exp=10` + `data.streak=1` + DB 의 feedback.completed=true + completed_at 비-null.
- **golden idempotent (2차 호출, 가산 없음)** — 1차 호출 후 즉시 같은 endpoint 다시 → 200 + `data.exp=10` (변동 없음) + `data.streak=1`.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **FEEDBACK_NOT_FOUND (cross-user)** — user A 토큰으로 user B 의 feedback ID → 404 + `FEEDBACK_NOT_FOUND` (보상 가산 없음).
- **FEEDBACK_NOT_FOUND (not-exists)** — feedbackId=999999 → 404 + `FEEDBACK_NOT_FOUND`.

##### Mock 정책
- 없음 (atomic UPDATE 만).

---

#### 22. `GET /api/feedbacks` → FeedbacksReadController.list

- **인증**: `authenticated`.
- **Test class**: `feedback.FeedbacksReadControllerTest#list`.
- **Snippet base**: `feedbacks/list`.

##### Request
- 없음 (header 만).

##### Response (golden 200)
`ApiResponse<List<FeedbackSummaryResponse>>`:
```json
{ "success": true, "data": [
    { "id":701, "title":"Greetings", "accuracy":86.5, "weakPhoneme":"ɔ", "createdAt":"2026-05-10T12:30:00Z" },
    { "id":702, "title":"My Cafe",    "accuracy":92.0, "createdAt":"2026-05-09T10:00:00Z" }
] }
```
- 정렬: `createdAt` 내림차순. `weakPhoneme` 는 `string?` (REFINED §5.6) — perfect 피드백 (errors 없음) 에서는 응답에서 키 자체가 **omit** 된다 (`@JsonInclude(NON_NULL)`).

##### 시나리오
- **golden (비-empty + perfect omission)** — 본인 feedback 2건 시드 (with-error + perfect 각 1건) → 200 + `data.length=2` + `createdAt` 내림차순 + `data[0].weakPhoneme="ɔ"` (with-error, 키 존재) + `jsonPath('$.data[1].weakPhoneme').doesNotExist()` (perfect, 키 omit, REFINED §5.6 nullable contract).
- **golden (empty)** — 본인 feedback 0건 → 200 + `data=[]`.
- **golden (cross-user 격리)** — A 의 feedback 1건 + B 의 feedback 1건 → A 토큰으로 GET → 200 + `data.length=1` + `data[0]` 가 A 것.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).

##### Mock 정책
- 없음.

---

#### 23. `GET /api/feedbacks/{feedbackId}` → FeedbacksReadController.detail

- **인증**: `authenticated`.
- **Test class**: `feedback.FeedbacksReadControllerTest#detail`.
- **Snippet base**: `feedbacks/detail`.

##### Request
- Path: `feedbackId` (Long).

##### Response (golden 200)
`ApiResponse<FeedbackResponse>` (#19 과 동일 schema).

##### 시나리오
- **golden (with-error)** — 본인 feedback (errors 있음) → 200 + `data.weakPhoneme="ɔ"` (키 존재) + `data.practiceWord` 비-empty + `data.guidanceKr` 비-empty + `data.errors.length>=1`.
- **golden (perfect)** — 본인 perfect feedback (errors=[]) → 200 + `jsonPath('$.data.weakPhoneme').doesNotExist()` + `data.practiceWord` 비-empty (REFINED §5.4 non-null contract) + `data.guidanceKr` 비-empty + `data.errors=[]` + `data.accuracy=100.0`.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **FEEDBACK_NOT_FOUND (cross-user)** — A 토큰 + B 의 feedback ID → 404 + `FEEDBACK_NOT_FOUND`.
- **FEEDBACK_NOT_FOUND (not-exists)** — 999999 → 404 + `FEEDBACK_NOT_FOUND`.

##### Mock 정책
- 없음.

---

### 그룹 G — Stats / Ranking / TTS (3 endpoint)

#### 24. `GET /api/stats/me` → StatsController.me

- **인증**: `authenticated`.
- **Test class**: `stats.StatsControllerTest#me`.
- **Snippet base**: `stats/me`.

##### Request
- Query: `year?` (Integer, 미지정 시 KST 현재 연도), `month?` (Integer 1-12, 미지정 시 현재 월).

##### Response (golden 200)
`ApiResponse<StatsResponse>`:
```json
{ "success": true, "data": {
    "streak": 3, "exp": 1240,
    "attendance": {
        "year": 2026, "month": 5,
        "days": { "1": 1, "2": 2, "3": 3, "5": 1 }
    },
    "weeklyErrors": [
        { "sound": "ɔ", "count": 7 },
        { "sound": "θ", "count": 4 }
    ],
    "badges": [
        { "id": "FIRST_FEEDBACK", "name": "첫 피드백", "achieved": true },
        { "id": "STREAK_7",       "name": "7일 연속",  "achieved": false }
    ]
} }
```
- `attendance.days` 는 `Map<dayOfMonth(int), accumulatedStreak(int)>`. **출석 없는 날은 키 omit**. day=N 의 값은 그 날까지의 누적 streak.
- `weeklyErrors`: 최근 7일의 음소 빈도 top-N (정렬: count 내림차순).
- `badges`: 정의된 모든 배지 ID + 달성 여부.

##### 시나리오
- **golden (비-empty)** — `seedUserA()` (exp=100, streak=3) + 5월 1/2/3/5 일에 completed_at 가진 feedback 시드 → GET `/api/stats/me?year=2026&month=5` → 200 + `data.streak=3` + `data.attendance.days = {1:1,2:2,3:3,5:1}` (4 일 키 omit).
- **golden (empty)** — completion 0건 → 200 + `data.streak=0` + `data.exp=0` + `data.attendance.days = {}` + `data.weeklyErrors=[]`.
- **golden (year/month 미지정)** — query 없이 GET → 200 + `data.attendance.year` / `month` 가 KST 현재 연/월.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **INVALID_REQUEST (year/month 범위 위반)** — `?month=13` → 400 + `INVALID_REQUEST`.
- **USER_NOT_FOUND** — `bearer(999999)` → 404 + `USER_NOT_FOUND`.

##### Mock 정책
- 없음 (DB only).

---

#### 25. `GET /api/ranking/today` → RankingController.today

- **인증**: `authenticated`.
- **Test class**: `ranking.RankingControllerTest#today`.
- **Snippet base**: `ranking/today`.

##### Request
- 없음 (header 만).

##### Response (golden 200)
`ApiResponse<RankingResponse>`:
```json
{ "success": true, "data": {
    "unitTitle": "Greetings",
    "myRank": 3,
    "totalUsers": 100,
    "myAccuracy": 92.5,
    "entries": [
        { "rank":1, "nickname":"top_user", "accuracy":98.0, "isMe":false },
        { "rank":2, "nickname":"runner",   "accuracy":95.0, "isMe":false },
        { "rank":3, "nickname":"Alice",    "accuracy":92.5, "isMe":true  }
    ]
} }
```
- `unitTitle`: 오늘 추천 학습 단위 (script) 의 제목.
- `entries`: 상위 N + 본인 (rank 와 `isMe=true`).
- `myRank` / `totalUsers` / `myAccuracy`: 본인 통계.

##### 시나리오
- **golden** — DemoRankingEntry 시드 + `seedUserA()` 의 본인 점수 → 200 + `data.entries` 비-empty + 본인 entry 의 `isMe=true` 정확히 1개.
- **golden (본인 미참여)** — `seedUserA()` 만 있고 오늘 학습 0건 → 200 + `data.myRank=null` (또는 omit) + `data.entries` 는 데모만.
- **unauthorized** — 401.
- **INVALID_TOKEN** — malformed / expired 토큰 → 401 + `error.code="INVALID_TOKEN"`. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).

##### Mock 정책
- 없음 (DemoRankingEntry 는 yaml 또는 SQL seed).

---

#### 26. `POST /api/tts` → TtsController.synthesize

- **인증**: `authenticated`. (§2.3 SecurityConfig 매핑: `/api/auth/**`, `/api/health`, `/error`, `/actuator/health` 만 `permitAll`, 나머지 `/api/**` 는 모두 `authenticated`. `/api/tts` 는 후자에 속한다.)
- **Test class**: `tts.TtsControllerTest#synthesize`.
- **Snippet base**: `tts/synthesize`.

##### Request
`TtsRequest`:

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `text` | String | `@NotBlank`, `@Size(max=500)` |
| `lang` | String? | null 허용 (기본 영어) |

```json
{ "text": "Welcome to the coffee shop." }
```

##### Response (golden 200)
- **본문 형식**: raw bytes (MP3). **응답이 `ApiResponse` envelope 이 아님** — 본 endpoint 만 예외.
- HTTP status 200, Content-Type `audio/mpeg`, body 가 비-empty `byte[]`.

##### 시나리오
- **golden** — `seedUserA()` + `bearer(userA)` + mock `ttsClient.synthesize(text, lang)` returns `byte[] {0x49,0x44,0x33,...}` (MP3 magic) → POST `{"text":"Welcome to the coffee shop."}` (Authorization 헤더 포함) → 200 + `Content-Type: audio/mpeg` + body length > 0.
- **unauthorized** — Authorization 헤더 미포함 + `{"text":"..."}` → 401 + `apiError("UNAUTHORIZED")`. Content-Type `application/json` (audio 가 아니라 envelope JSON).
- **INVALID_TOKEN** — malformed / expired 토큰 + `{"text":"..."}` → 401 + `apiError("INVALID_TOKEN")` JSON envelope. 일괄 검증은 §1.5.1 parametrized 테스트가 담당 (본 endpoint 도 그 표에 포함).
- **INVALID_REQUEST (검증 실패)** — bearer + `{"text":""}` (빈 text) 또는 `{"text":"<501자 이상>"}` → 400 + `apiError("INVALID_REQUEST")` JSON envelope. REFINED §4.12 의 endpoint-specific 명세를 따른다 (§부록 A 의 일반 handler 매핑인 `MethodArgumentNotValidException → VALIDATION_FAILED` 보다 endpoint-specific 명세가 우선).
- **MODEL_SERVER_UNAVAILABLE** — bearer + mock 이 throws `BusinessException(MODEL_SERVER_UNAVAILABLE, ...)` → 503 + `apiError("MODEL_SERVER_UNAVAILABLE")` JSON envelope.
- **MODEL_SERVER_ERROR** — bearer + mock 이 throws → 502 + `apiError("MODEL_SERVER_ERROR")` JSON envelope.

##### Mock 정책
- `ttsClient.synthesize(String text, String lang)`: mock 으로 stub 처리. 정상은 MP3 magic 바이트 배열, 에러는 `BusinessException` throw.
- **검증 매핑 노트**: TTS 컨트롤러 구현은 `@Valid TtsRequest` 의존 대신 수동 검증으로 `BusinessException(INVALID_REQUEST, ...)` 또는 `IllegalArgumentException` 을 throw 하고, 후자는 GlobalExceptionHandler 가 INVALID_REQUEST 로 매핑하도록 구성해야 한다. `@Valid` 만 의존하면 `MethodArgumentNotValidException → VALIDATION_FAILED` 로 매핑되어 본 plan 의 회귀 기준 위반.

---

## §4. 도메인 invariant 시나리오 (Tier 2, 15개)

각 시나리오는 별도 service-level 통합 테스트 클래스로 구현 (`AbstractServiceIntegrationTest` 상속). MockMvc 도 함께 사용하지만 1차 assertion 은 service / DB 상태.

### I1. Cross-user 차단 (Recording / Feedback / Session)
- **Test class**: `invariant.CrossUserAccessInvariantTest`.
- **사전 셋업**: `seedUserA()` + `seedUserB()`. B 의 session/recording/feedback 각각 1건 시드.
- **호출**: A 의 토큰으로 — `GET /api/sessions/{B의 sessionId}`, `GET /api/feedbacks/{B의 feedbackId}`, `POST /api/feedback/{B의 feedbackId}/complete`, `DELETE /api/sessions/{B의 sessionId}`.
- **Assertion**: 모두 404 + 해당 `*_NOT_FOUND` ErrorCode + B 데이터 unchanged + B 의 user.exp 변동 없음.

### I2. Cross-parent 거절 — Recording 정적 팩토리 3종
- **Test class**: `invariant.RecordingFactoryInvariantTest`.
- **사전 셋업**: scriptA + stepA (scriptA 소속), scriptB + stepB (scriptB 소속). `seedUserA()`. sessionA + sentenceA, sessionA' + sentenceA' (다른 세션).
- **호출**: entity factory 직접 호출:
  - `Recording.forScriptStep(userA, scriptA, stepB, ...)` — step 의 부모 script 가 다름.
  - `Recording.forSessionSentence(userA, sessionA, sentenceA', ...)` — sentence 의 부모 session 이 다름.
  - `Recording.forSessionFreeForm(userB, sessionA, ...)` — session.user 가 A 인데 B 로 호출.
- **Assertion**: 세 호출 모두 `IllegalArgumentException` throw + DB 에 Recording 행 0개 추가 + (HTTP 면 호출이라면) 500 + `INTERNAL_ERROR`.

### I3. Cross-parent 거절 — PronunciationFeedback.create
- **Test class**: `invariant.FeedbackFactoryInvariantTest`.
- **사전 셋업**: `seedUserA()` + `seedUserB()` + B 의 sessionB.
- **호출**: `PronunciationFeedback.create(userA, null, sessionB, ...)` 직접.
- **Assertion**: `IllegalArgumentException` + DB 에 feedback 행 0개 추가.

### I4. Recording CHECK 제약 — 양쪽 NOT NULL raw INSERT 거절
- **Test class**: `invariant.RecordingChecksInvariantTest` (**Tier 3 — `@DataJpaTest` 권장**, 본 plan 의 2-tier 정책 예외).
- **사전 셋업**: scriptA + sessionA + userA persist.
- **호출**: TestEntityManager 또는 raw JdbcTemplate 으로 `INSERT INTO recordings (script_id, session_id, ...) VALUES (scriptA.id, sessionA.id, ...)` (양쪽 NOT NULL).
- **Assertion**: `DataIntegrityViolationException` (또는 `JdbcSQLIntegrityConstraintViolationException`) throw. DB CHECK 식이 양쪽 NOT NULL 을 거절함.

### I5. Session 대본 갱신 후 녹음 보존
- **Test class**: `invariant.SessionUpdateScriptInvariantTest`.
- **사전 셋업**: `seedUserA()` + sessionA (sentence 2개) + Recording 1건 (`session_sentence_id=sentenceA[0].id`, `target_text_snapshot="Hello!"`).
- **호출**: `PATCH /api/sessions/{sessionA.id}` body `{"scriptText":"New text. Another."}`.
- **Assertion**: 200 + DB 의 Recording 행 보존 + `recording.session_sentence_id == NULL` + `recording.target_text_snapshot == "Hello!"` (변경 없음) + sessionA.sentences 가 새 2개로 교체됨.

### I6. Session hard delete 후 history 보존
- **Test class**: `invariant.SessionDeleteInvariantTest`.
- **사전 셋업**: `seedUserA()` + sessionA + Recording 1건 (session-flow) + Feedback 1건 (session-flow, completed=true).
- **호출**: `DELETE /api/sessions/{sessionA.id}`.
- **Assertion**: 204/200 + DB 의 sessions 행 0개 + `recording.session_id == NULL` + `recording.target_text_snapshot` 보존 + `feedback.session_id == NULL` + `feedback.title` / `feedback.accuracy` 보존 + CHECK 위반 없음 (XOR 식이 양쪽 NULL 도 허용).

### I7. 완료 동시성 — `complete` 두 스레드 → EXP 정확히 한 번 가산
- **Test class**: `invariant.FeedbackCompleteConcurrencyTest`.
- **사전 셋업**: `seedUserA()` (exp=0) + feedback 1건 (completed=false).
- **호출**: `CountDownLatch latch = new CountDownLatch(1)` + 두 스레드가 latch.await() 후 동시에 `POST /api/feedback/{id}/complete` 호출.
- **Assertion**: 두 호출 모두 200 + `data.exp` 가 둘 다 같은 최종값 10 (idempotent) + DB 의 user.exp = 10 정확 + feedback.completed=true + completed_at 비-null.

### I8. Cross-context generate 거절
- **Test class**: `invariant.FeedbackGenerateContextInvariantTest`.
- **사전 셋업**: `seedUserA()` + scriptA + session-flow recording (sessionA 소속, recording.session_id=sessionA.id, recording.script_id=null).
- **호출**: `POST /api/feedback/generate` body `{"scriptId":scriptA.id, "recordingIds":[그 recording.id]}`.
- **Assertion**: 404 + `error.code="RECORDING_NOT_FOUND"` (cross-context 도 not-found 로 통합) + DB 에 feedback 행 0개 추가.

### I9. ON DELETE SET NULL 끊긴 recording 의 generate
- **Test class**: `invariant.FeedbackGenerateOrphanedRecordingTest`.
- **사전 셋업**: `seedUserA()` + sessionA + session-flow recording 시드 → `DELETE /api/sessions/{sessionA.id}` 실행 (cascade SET NULL → recording.session_id=NULL).
- **호출**: `POST /api/feedback/generate` body `{"sessionId":<다른 session.id>, "recordingIds":[orphan recording.id]}` (또는 sessionA.id 가 더 이상 존재 안 함 케이스).
- **Assertion**: 404 + `RECORDING_NOT_FOUND` + DB 에 feedback 행 0개.

### I10. Cross-user complete 거절 (FEEDBACK_NOT_FOUND 보존)
- **Test class**: `invariant.FeedbackCompleteAuthInvariantTest`.
- **사전 셋업**: `seedUserA()` + `seedUserB()` (exp=0, streak=0) + B 의 feedback 1건 (completed=false).
- **호출**: A 토큰으로 `POST /api/feedback/{B의 feedbackId}/complete`.
- **Assertion**: 404 + `error.code="FEEDBACK_NOT_FOUND"` + DB 의 A.exp=0, A.streak=0 변동 없음 + B 의 feedback.completed=false 변동 없음.

### I11. Recording upload 부분 실패 시 storage 정리
- **Test class**: `invariant.RecordingUploadCompensationTest`.
- **사전 셋업**: `seedUserA()` + ScriptFixture + WAV. 임시 디렉토리 (`@TempDir Path tmpDir`) 를 `app.storage.localRoot` 로 override.
- **3 케이스**:
  1. mock `modelServerClient.analyze(...)` throws `BusinessException(MODEL_SERVER_UNAVAILABLE)` (storage.save 호출 전).
  2. cross-parent invariant 위반 (validateForXxx 또는 정적 팩토리 단계에서 throw).
  3. storage.save 성공 후 DB save 시점 강제 실패 (`@SpyBean` RecordingRepository 가 `save` throw `DataIntegrityViolationException`).
- **호출**: `POST /api/recordings` 각 케이스.
- **Assertion**: 각 케이스 모두 4xx/5xx 응답 + tmpDir 트리 walk 결과 .wav 파일 0개.

### I12. Recording upload commit-time 실패 시 storage 정리
- **Test class**: `invariant.RecordingUploadCommitTimeTest`.
- **사전 셋업**: `seedUserA()` + ScriptFixture + WAV. 임시 디렉토리. flush/commit 시점 강제 실패 (예: TestExecutionListener 에서 트랜잭션 commit 직전 throw, 또는 `@SpyBean` PlatformTransactionManager 의 commit 을 모킹).
- **호출**: `POST /api/recordings` (정상 입력).
- **Assertion**: 5xx 응답 + tmpDir 의 .wav 파일 0개 + storage.delete 호출 횟수 = 1, 시점은 `afterCompletion(STATUS_ROLLED_BACK)`.

### I13. Attendance 일자 정확도 + KST 자정 경계
- **Test class**: `invariant.StatsAttendanceTimezoneTest`.
- **사전 셋업**: `seedUserA()` + `@MockBean Clock`. day=N (예: 2026-05-10) KST 23:59 와 day=N+1 (2026-05-11) KST 00:30 두 시점에 각각 feedback 시드 + complete (`completedAt = 그 instant`).
- **호출 (1)**: `Clock.now()` = 2026-05-10 KST 23:59 → `POST /api/feedback/{id1}/complete` → DB 의 `completed_at` 가 `2026-05-10T14:59Z` (UTC).
- **호출 (2)**: `Clock.now()` = 2026-05-11 KST 00:30 → `POST /api/feedback/{id2}/complete` → `completed_at = 2026-05-10T15:30Z` (UTC).
- **호출 (3)**: `GET /api/stats/me?year=2026&month=5` (KST 기준).
- **Assertion**: 200 + `data.attendance.days[10] >= 1` + `data.attendance.days[11] >= 1` + DB session timezone 이 UTC 임에도 결과 일관 (Java-side bucketing 정책 — `i.atZone(KST).getDayOfMonth()` 로 분류).

### I14. streak ↔ attendance KST 자정 일치
- **Test class**: `invariant.RewardStreakTimezoneTest`.
- **사전 셋업**: `seedUserA()` (exp=0, streak=0, lastStudyAt=null) + `@MockBean Clock`. feedback 1건 (completed=false).
- **호출**: `Clock.now()` = 2026-05-10 KST 23:59:30 → `POST /api/feedback/{id}/complete` → `GET /api/members/me` + `GET /api/stats/me?year=2026&month=5`.
- **Assertion**:
  1. complete 응답 `data.streak == 1` + `data.exp == 10`.
  2. `/members/me` 응답 `data.streak == 1` (일치).
  3. `/stats/me` 응답 `data.attendance.days` 에 `"10": 1` 키 존재 (day=10 KST 기준).
  4. 다른 KST day (11 등) 키 omit.

### I15. wrongWords 비/empty
- **Test class**: `invariant.RecordingWrongWordsInvariantTest`.
- **사전 셋업**: `seedUserA()` + ScriptFixture + WAV.
- **2 케이스**:
  1. mock `modelServerClient.analyze(...)` returns `WATER_WITH_ERROR` (errors 비-empty) + mock `llmClient.summarizeRecording(...)` returns `LlmMockResponses.GUIDANCE_SIMPLE` (`wrongWords=[{water,0}]`).
  2. mock `analyze` returns `WATER_PERFECT` (errors=[]) + mock `llm` returns `GUIDANCE_PERFECT` (`wrongWords=[]`).
- **호출**: 각 케이스 `POST /api/recordings` (script-flow).
- **Assertion**:
  1. 201 + `data.wrongWords.length=1` + `data.wrongWords[0].word="water"` + `data.errors.length=1`.
  2. 201 + `data.wrongWords=[]` + `data.errors=[]`.

---

## §5. 실행 가이드

- `./gradlew test` — 전체 테스트 (Tier 1 + Tier 2).
- `./gradlew test --tests '*ControllerTest'` — Tier 1 만.
- `./gradlew test --tests '*InvariantTest'` — Tier 2 만.
- `./gradlew asciidoctor` — REST Docs HTML 생성 (`build/docs/asciidoc/index.html`).
- snippet 누락 검출: `build/generated-snippets/` 디렉토리 트리에 §1.8 명명 규칙대로 모든 snippet 이 생성됐는지 비교.

CI 권장: `./gradlew test asciidoctor` 를 PR 별로 실행. snippet 트리가 base 와 달라지면 contract 변경 신호.

---

## §부록 A. ErrorCode × Endpoint 매트릭스 (Tier 1 망라성)

각 셀: trigger 조건 한 줄 또는 `—` (해당 endpoint 가 throw 하지 않음).

| ErrorCode \ Endpoint (#) | 1 health | 2 signup | 3 login | 4 chk-u | 5 chk-e | 6 oauth-demo | 7 me | 8 nick | 9 trk-list | 10 trk-detail | 11 scr-rec | 12 scr-detail | 13 ses-list | 14 ses-create | 15 ses-detail | 16 ses-patch | 17 ses-delete | 18 rec-upload | 19 fb-gen | 20 fb-retry | 21 fb-complete | 22 fb-list | 23 fb-detail | 24 stats | 25 ranking | 26 tts |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| INVALID_REQUEST           | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | mode 위반/multipart 누락 | XOR 위반 | multipart 누락/잘못된 part name/사이즈 초과 | — | — | — | year/month 범위 위반 | — | NotBlank/Size 검증 실패 |
| VALIDATION_FAILED         | — | NotBlank/Pattern/AssertTrue | NotBlank | NotBlank | NotBlank | — | — | NotBlank/Size | — | — | — | — | — | NotBlank/Size | — | (옵션) | — | — | recordingIds NotEmpty | — | — | — | — | — | — | — |
| UNAUTHORIZED              | — | — | — | — | — | — | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 | 헤더 누락 |
| INVALID_TOKEN             | — | — | — | — | — | — | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) | 변조/만료 토큰 (§1.5.1) |
| LOGIN_FAILED              | — | — | bad creds | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| USERNAME_DUPLICATED       | — | dup user | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| EMAIL_DUPLICATED          | — | dup email | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| USER_NOT_FOUND            | — | — | — | — | — | — | bad uid | bad uid | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | bad uid | — | — |
| TRACK_NOT_FOUND           | — | — | — | — | — | — | — | — | — | bad id | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| SCRIPT_NOT_FOUND          | — | — | — | — | — | — | — | — | — | — | — | bad id | — | — | — | — | — | bad id | bad id | — | — | — | — | — | — | — |
| STEP_NOT_FOUND            | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | bad id | — | — | — | — | — | — | — | — |
| SESSION_NOT_FOUND         | — | — | — | — | — | — | — | — | — | — | — | — | — | — | bad/x-user | bad/x-user | bad/x-user | bad/x-user | bad/x-user | — | — | — | — | — | — | — |
| SESSION_SENTENCE_NOT_FOUND| — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | bad id | — | — | — | — | — | — | — | — |
| RECORDING_NOT_FOUND       | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | x-user/x-context/orphan | — | — | — | — | — | — | — |
| FEEDBACK_NOT_FOUND        | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | bad/x-user | bad/x-user | — | bad/x-user | — | — | — |
| AUDIO_DECODE_FAILED       | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | bad WAV | — | bad WAV | — | — | — | — | — | — |
| MODEL_SERVER_UNAVAILABLE  | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | mock throws | mock throws | mock throws | — | — | — | — | — | mock throws |
| MODEL_SERVER_ERROR        | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | mock throws | mock throws | mock throws | — | — | — | — | — | mock throws |
| INTERNAL_ERROR            | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | invariant 1단계 누락 | — | — | — | — | — | — | — | — |

> `INTERNAL_ERROR` 는 정상 흐름에서 발생하지 않는 방어적 ErrorCode (서비스가 두 단계 검증 1단계를 빠뜨려 entity invariant 가 throw 한 경우). Tier 2 의 I2 시나리오가 이를 trigger.

---

## §부록 B. 공통 fixture 카탈로그 (자기완결 본문)

### B.1 사용자
- `UserFixture.SEED_USER_A` — username=`alice`, email=`alice@test.com`, nickname=`Alice`, password 평문=`Pa$$w0rd!` (BCrypt 저장), exp=0, streak=0, lastStudyAt=null.
- `UserFixture.SEED_USER_B` — username=`bob`, email=`bob@test.com`, nickname=`Bob`, password=`Pa$$w0rd!`.
- `UserFixture.DEMO_GOOGLE` — username=`demo_google`, email=`demo@google.example`, nickname=`Demo`. 6번 endpoint 의 upsert 대상.

### B.2 학습 콘텐츠
- `TrackFixture.PRESET_TRACK_BEGINNER` — id=auto, title="Beginner Conversations", displayOrder=1, chapterCount=2.
- `ScriptFixture.PRESET_SCRIPT_GREETINGS` — id=auto, title="Greetings", content="Hello! How are you?", difficulty=`BEGINNER`, isPreset=true, steps=[INTRO(orderIndex=0), RECORD(orderIndex=1, targetText="Hello!")].
- `ScriptFixture.PRESET_SCRIPT_WATER` — title="At the Cafe", steps=[INTRO, RECORD(targetText="I want some water.")].
- `LearningStepFixture.STEP_RECORD_WATER` — script=PRESET_SCRIPT_WATER, kind=`RECORD`, targetText="I want some water.".

### B.3 세션
- `SessionFixture.SESSION_HELLO_FOR_A` — user=SEED_USER_A, title="My Cafe", scriptText="Hello! Welcome.", sentences=[{0,"Hello!"},{1,"Welcome."}], favorite=false.
- `SessionFixture.SESSION_FOR_B` — user=SEED_USER_B, title="Bob's Session", ...

### B.4 녹음 / 피드백
- `RecordingFixture.SCRIPT_FLOW_WATER_FOR_A` — user=A, script=PRESET_SCRIPT_WATER, step=STEP_RECORD_WATER, target_text_snapshot="I want some water.", audio_path="users/1/202605/abc.wav", perceived/canonical/peakSoftmax/errors_json/guidance_kr/wrong_words_json 모두 채움.
- `RecordingFixture.SESSION_SENTENCE_FOR_A` — user=A, session=SESSION_HELLO_FOR_A, sentence=index 0.
- `FeedbackFixture.SCRIPT_FLOW_FOR_A` — user=A, script=PRESET_SCRIPT_WATER, accuracy=86.5, weakPhoneme="ɔ", practiceWord="water", guidanceKr="ɔ 발음을 더 둥글게.", completed=false, completed_at=null.
- `FeedbackFixture.SESSION_FLOW_FOR_A` — user=A, session=SESSION_HELLO_FOR_A, ...

### B.5 WAV / 모델 mock 응답
- `WavFixtures.SILENT_1S` — `byte[]` 길이 32044 (16kHz mono PCM 1초 무음 + WAV header). resource path `/test-wav/silent-1s.wav`.
- `WavFixtures.WATER_1S` — "water" 발음 mock WAV. resource path `/test-wav/water-1s.wav`.
- `AnalyzeMockResponses.WATER_PERFECT` — `ModelAnalyzeResponse(perceived=["w","ɔ","t","ɚ"], canonical=["w","ɔ","t","ɚ"], peakSoftmax=[0.95,0.95,0.95,0.95], alignment=[...all equal], errors=[], per=0.0, durationSec=1.0)`.
- `AnalyzeMockResponses.WATER_WITH_ERROR` — `ModelAnalyzeResponse(perceived=["w","ʌ","t","ɚ"], canonical=["w","ɔ","t","ɚ"], peakSoftmax=[0.91,0.62,0.88,0.79], alignment=[...with substitution at index 1], errors=[{op:"substitution",canonical:"ɔ",perceived:"ʌ",canonicalIndex:1}], per=0.25, durationSec=1.0)`.

### B.6 LLM mock 응답
- `LlmMockResponses.GUIDANCE_SIMPLE` — `RecordingGuidance(guidanceKr="ɔ 모음을 더 둥글게.", wrongWords=[new WrongWord("water", 0)])`.
- `LlmMockResponses.GUIDANCE_PERFECT` — `RecordingGuidance(guidanceKr="발음이 자연스럽습니다.", wrongWords=[])`.
- `LlmMockResponses.SUMMARIZE_FEEDBACK_DEFAULT` — `String "ɔ 발음을 더 둥글게."` (FeedbackService.generate 의 `llmClient.summarizeFeedback(...)` 반환값).
- `LlmMockResponses.RETRY_GUIDANCE_DEFAULT` — `String "이번엔 더 가까워졌어요."` (`llmClient.retryGuidance(...)`).
- `LlmMockResponses.PRACTICE_WORD_DEFAULT` — `String "water"` (`llmClient.suggestPracticeWord(...)`).

### B.7 TTS mock
- `TtsMockResponses.MP3_BYTES` — `byte[] {0x49,0x44,0x33, ... 100 bytes}` (MP3 magic + payload). `ttsClient.synthesize(...)` stub.

---

## §부록 D. 회귀 검증 체크리스트 (산출 문서 사용자가 직접 돌리는 체크)

- [ ] §3 의 26 endpoint 모두에 golden + (인증 필요 시) unauthorized + 그 endpoint 가 throw 가능한 모든 ErrorCode 시나리오 본문 빠짐없이.
- [ ] §부록 A 매트릭스의 19 ErrorCode 가 최소 1개 endpoint 에 trigger 됨 (`INTERNAL_ERROR` 는 Tier 2 I2 가 trigger).
- [ ] §4 의 15 invariant 시나리오 모두에 사전 셋업 / 호출 / assertion 본문이 풀로 작성됨 (표만 두지 않음).
- [ ] §부록 B 의 모든 fixture (SEED_USER_A/B, DEMO_GOOGLE, PRESET_SCRIPT_*, SESSION_*, RECORDING_*, FEEDBACK_*, WAV_*, AnalyzeMockResponses, LlmMockResponses, TtsMockResponses) 가 본문 시나리오 어디선가 1회 이상 사용됨.
- [ ] 본 문서는 `API_SPEC_REFINED.md` 의 contract 차이를 모두 본문에 인라인했다. 외부 cross-ref ("자세한 내용은 SPEC 참조", "다른 브랜치의 코드 참조") 없이 자기완결.
- [ ] §1.5.1 parametrized 테스트가 20개 보호 endpoint 모두 missing/malformed/expired 3 케이스 일괄 검증. 매트릭스의 `INVALID_TOKEN` 행 모든 셀이 본 테스트로 trigger 됨.
- [ ] §3.17 DELETE /api/sessions/{id} 골든이 200 + `ApiResponse{success:true}` envelope 만 수용 (204 비수용). REFINED §1.1 void 작업 contract 정합.
- [ ] §1.8 snippet 명명 규칙과 §3 본문의 모든 snippet 이름이 일치.
- [ ] CI 에서 `./gradlew test asciidoctor` 한 번에 통과.

---

## §부록 E. 개정 이력

| 일자 | 항목 |
| --- | --- |
| 2026-05-10 | 초판. TDD 재구현용 자기완결 회귀 방지 테스트 플랜. 26 endpoint × HTTP contract (Tier 1) + 15 도메인 invariant 시나리오 (Tier 2). REST Docs MockMvc 표준. |
| 2026-05-10 (rev2) | Codex round 1 4 finding 반영 — (F1) §3.14 POST /api/sessions 의 SessionCreateRequest 에서 scriptText 필드 제거 (develop: title 만), sentence split 시나리오는 PATCH 로만. (F2) §3.26 POST /api/tts 인증을 authenticated 로 정정 (§2.3 SecurityConfig 와 일관), unauthorized 시나리오 신설, golden 에 Bearer 추가. (F3) §3.20 RetryWordResponse 를 {correct, perceived, canonical, score, guidanceKr} 5 필드로 정정 (develop record 와 일치, word/stepScore 제거), correct=true/false 두 golden 분기. (F4) §3.1 GET /api/health 응답을 ApiResponse&lt;Map&lt;String,Object&gt;&gt; envelope 으로 정정, data={status:"UP", service:"echo-app-backend", timestamp:ISO}. §부록 A 매트릭스 #26 TTS 행에 UNAUTHORIZED/INVALID_TOKEN trigger 추가. |
| 2026-05-10 (rev3) | Codex round 2 3 finding 반영 — (F1) develop 참조 제거 + main 의 root Gradle layout 으로 셋업 경로 정정 (`backend/src/test/...` → `src/test/...`, `backend/build.gradle` → root `build.gradle`). 자기완결 약속의 비교 대상 목록에서 develop 단어 제거. §0 메타에 "API_SPEC.md 가 contract source-of-truth" 명시. (F2) §3.19 POST /api/feedback/generate 에 `MODEL_SERVER_UNAVAILABLE` (503) / `MODEL_SERVER_ERROR` (502) 시나리오 신설 + mock 정책 갱신 (구체 컴포넌트 이름 비강제). §부록 A 매트릭스 #19 의 두 셀을 `mock throws` 로 정정. (F3) §3.20 retry-word 에 `INVALID_REQUEST` (multipart missing part / wrong part name / size 초과) 시나리오 신설. §부록 A 매트릭스 #20 의 INVALID_REQUEST 셀 정정. |
| 2026-05-10 (rev4) | Contract source-of-truth 를 `API_SPEC.md` → `API_SPEC_REFINED.md` 로 전환. (C1) §0 메타에 nullable `?` + `@JsonInclude(NON_NULL)` 키 omission 정책 명시 (REFINED §1.3). (C2) §3.18 Recording 에 strict 규정 노트 ("mode 위반 = 400 INVALID_REQUEST" 를 본 plan 자체 contract 로 유지) + cross-tenant `*_NOT_FOUND` 정책 한 줄 + INVALID_REQUEST 시나리오에 4 케이스 (두 mode 혼합 / 컨텍스트 모두 누락 / 불완전 script-flow / 불완전 session-sentence) 명시. (C3, C4) §3.19 generate 응답 예시에 perfect 케이스 (`weakPhoneme` omit) 추가, perfect / script-flow 골든에 `practiceWord` / `guidanceKr` 비-empty assertion + REFINED §5.4 non-null fallback 체인 contract 노트. (C5) §3.22 list 응답 예시의 `weakPhoneme: null` → 키 omit 정정, perfect 골든 분기 통합. (C6) §3.12 Script detail 응답 예시의 INTRO `targetText: null` → 키 omit 정정, 골든 assertion 을 INTRO `doesNotExist` + RECORD 비-empty 로 강화. §3.23 detail 에 with-error / perfect 두 골든 분기 분할. §3.19 / §3.20 mock 정책의 `API_SPEC §4.4` 잔재를 REFINED §4.4 로 정정. §부록 A 매트릭스 / §4 Tier 2 invariant / 다른 endpoint 변경 없음. |
| 2026-05-11 (rev5) | Codex round 3 3 finding 반영 — (F1) §1.5 의 401 단일화 (UNAUTHORIZED) 를 REFINED §2.2 분리 (missing → UNAUTHORIZED, malformed/expired → INVALID_TOKEN) 로 정정. §1.5.1 신설: 20개 보호 endpoint (auth/health 제외) 의 (method, path, minimal body) dispatch 표 + JUnit5 parametrized 테스트 명세 (missing/malformed/expired 3 케이스 × 20 endpoint = 60 assertion) 인라인. per-endpoint §3 시나리오 20곳에 `INVALID_TOKEN — see §1.5.1` 한 줄 참조 추가. §부록 A 매트릭스 INVALID_TOKEN 행 셀을 `변조/만료 토큰 (§1.5.1)` 로 강화. (F2) §3.26 TTS 의 VALIDATION_FAILED → INVALID_REQUEST 로 정정 (REFINED §4.12 endpoint-specific 명세가 §부록 A 의 일반 handler 매핑보다 우선이라는 원칙). mock 정책에 "TTS 는 @Valid 대신 수동 검증으로 INVALID_REQUEST 매핑 필수" 한 줄. §부록 A 매트릭스 #26 의 INVALID_REQUEST / VALIDATION_FAILED 두 셀 swap. (F3) §3.17 DELETE /api/sessions/{id} 응답을 200 + `ApiResponse{success:true}` envelope 만 수용으로 strict 화 (204 비수용, REFINED §1.1 void 작업 contract). 시나리오 두 줄에서 "204" 표현 제거. §부록 D 체크리스트 두 줄 추가. |

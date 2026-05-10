# Echo Backend API 명세서

> **대상 서버:** `com.capstoneecho.echo_back` (Spring Boot 4.0.5, Java 25)
> **작성일:** 2026-05-05
> **베이스 URL:** `http(s)://<host>:<port>` (운영 도메인은 환경별 설정)
> **컨텐츠 타입(요청):** `application/json` 기본, 일부 엔드포인트는 `multipart/form-data`
> **컨텐츠 타입(응답):** `application/json` 기본, TTS는 `audio/mpeg`
> **날짜/시간 포맷:** ISO-8601(`Instant` 직렬화). 예) `2026-05-05T14:23:11.123Z`

---

## 1. 공통 응답 포맷

모든 REST 응답은 `ApiResponse<T>` envelope으로 직렬화됩니다(TTS 바이너리 응답 제외). `@JsonInclude(NON_NULL)`이 적용되어 있어 `null` 필드는 직렬화에서 제외됩니다.

### 1.1 성공 응답

```json
{
  "success": true,
  "data": { /* 엔드포인트별 페이로드 */ }
}
```

데이터가 없는 작업(예: `DELETE /api/sessions/{id}`)은 `data` 필드가 생략됩니다.

```json
{ "success": true }
```

### 1.2 실패 응답

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE_NAME",
    "message": "사용자에게 노출 가능한 한글 메시지"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `success` | boolean | `false` 고정 |
| `error.code` | string | `ErrorCode` enum 이름(부록 §A 참조) |
| `error.message` | string | 기본 메시지 또는 호출 지점에서 보강된 메시지 |

HTTP 상태 코드는 `ErrorCode`별 매핑(부록 §A)에 따릅니다.

---

## 2. 인증 & 보안 모델

- **세션 정책:** `STATELESS`. 서버는 세션을 보관하지 않으며 모든 요청은 JWT로 검증.
- **인증 방식:** `Authorization: Bearer <accessToken>` 헤더(JWT).
- **CSRF:** 비활성화(API 전용).
- **CORS:** `AppProperties.cors().allowedOrigins`에서 허용 origin 관리. 메서드 `GET/POST/PUT/PATCH/DELETE/OPTIONS`, 헤더 `*`, `Authorization`/`Content-Disposition` 노출, `allowCredentials=true`, `maxAge=1h`.
- **비밀번호 해시:** BCrypt.

### 2.1 공개/보호 경로 매트릭스 (`SecurityConfig`)

| 경로 패턴 | 인증 |
| --- | --- |
| `/api/auth/**` | 공개 |
| `/api/health` | 공개 |
| `/error` | 공개 |
| `/actuator/health` | 공개 |
| 그 외 `/api/**` | **인증 필요(JWT)** |
| 그 외 모든 경로 | 공개 |

> 주의: 컨트롤러가 `@CurrentUser`를 사용하지 않더라도(`/api/scripts/**`, `/api/tracks/**`, `/api/tts`, `/api/recordings`, `/api/sessions`, `/api/feedback*`, `/api/members/**`, `/api/ranking/**`, `/api/stats/**`) `SecurityConfig`의 `requestMatchers("/api/**").authenticated()`에 의해 모두 JWT가 필요합니다.

### 2.2 인증 실패 처리

- 미인증 요청: `JwtAuthEntryPoint`가 `401 Unauthorized` 반환.
- 잘못된/만료된 토큰: `INVALID_TOKEN` 코드(`401`).
- `GlobalExceptionHandler`가 Spring `AuthenticationException`/`AccessDeniedException`을 `UNAUTHORIZED`(`401`)로 정형화.

---

## 3. 엔드포인트 인덱스

엔드포인트는 도메인 단위로 묶었습니다(자세한 스펙은 §4 동일 이름 절 참조).

### 3.1 Member (계정 · 인증)

| 메서드 | 경로 | 인증 | 설명 | 상세 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/signup` | — | 회원가입 | §4.2 |
| POST | `/api/auth/login` | — | 로그인 | §4.2 |
| POST | `/api/auth/check-username` | — | 아이디 중복 확인 | §4.2 |
| POST | `/api/auth/check-email` | — | 이메일 중복 확인 | §4.2 |
| GET | `/api/auth/oauth2/google/demo` | — | 데모용 Google OAuth2 로그인 | §4.2 |
| GET | `/api/members/me` | JWT | 내 프로필 조회 | §4.3 |
| PATCH | `/api/members/me/nickname` | JWT | 내 닉네임 변경 | §4.3 |

### 3.2 Learning (학습 콘텐츠 · 세션)

| 메서드 | 경로 | 인증 | 설명 | 상세 |
| --- | --- | --- | --- | --- |
| GET | `/api/tracks` | JWT | 트랙 목록 | §4.9 |
| GET | `/api/tracks/{trackId}` | JWT | 트랙 상세(챕터 포함) | §4.9 |
| GET | `/api/scripts/recommended/today` | JWT | 오늘의 추천 스크립트 | §4.8 |
| GET | `/api/scripts/{scriptId}` | JWT | 스크립트 상세(학습 단계 포함) | §4.8 |
| GET | `/api/sessions` | JWT | 내 세션 목록 | §4.7 |
| POST | `/api/sessions` | JWT | 세션 생성 | §4.7 |
| GET | `/api/sessions/{sessionId}` | JWT | 세션 상세 | §4.7 |
| PATCH | `/api/sessions/{sessionId}` | JWT | 세션 부분 수정 | §4.7 |
| DELETE | `/api/sessions/{sessionId}` | JWT | 세션 삭제 | §4.7 |

### 3.3 Pronunciation Evaluation (녹음 · 피드백 · TTS)

| 메서드 | 경로 | 인증 | 설명 | 상세 |
| --- | --- | --- | --- | --- |
| POST | `/api/recordings` | JWT | 녹음 업로드(멀티파트) | §4.6 |
| POST | `/api/feedback/generate` | JWT | 종합 피드백 생성 | §4.4 |
| POST | `/api/feedback/{feedbackId}/retry-word` | JWT | 약점 단어 재시도(멀티파트) | §4.4 |
| POST | `/api/feedback/{feedbackId}/complete` | JWT | 피드백 완료 보상 적립 | §4.4 |
| GET | `/api/feedbacks` | JWT | 내 피드백 목록 | §4.5 |
| GET | `/api/feedbacks/{feedbackId}` | JWT | 피드백 상세 | §4.5 |
| POST | `/api/tts` | JWT | 영문 텍스트 → MP3 합성 | §4.12 |

### 3.4 Statistics (통계 · 랭킹)

| 메서드 | 경로 | 인증 | 설명 | 상세 |
| --- | --- | --- | --- | --- |
| GET | `/api/stats/me` | JWT | 내 통계(스트릭/배지/주간 약점) | §4.11 |
| GET | `/api/ranking/today` | JWT | 오늘의 학습 단위 랭킹 | §4.10 |

### 3.5 System

| 메서드 | 경로 | 인증 | 설명 | 상세 |
| --- | --- | --- | --- | --- |
| GET | `/api/health` | — | 헬스체크 | §4.1 |

> **총 26개** — Member 7 / Learning 9 / Pronunciation Evaluation 7 / Statistics 2 / System 1.

---

## 4. 엔드포인트 상세

### 4.1 Health

#### `GET /api/health`
- 목적: 서버 부팅 확인.
- 인증: 없음.
- 응답: `ApiResponse<Map<String, Object>>`

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "service": "echo-app-backend",
    "timestamp": "2026-05-05T14:23:11.123Z"
  }
}
```

---

### 4.2 Auth (`/api/auth`)

모두 공개. 응답 envelope은 `ApiResponse<T>`. 회원가입은 `201 Created`로 반환.

#### `POST /api/auth/signup`
- 목적: 일반 회원가입 + JWT 즉시 발급.
- 요청 본문: `SignupRequest`

| 필드 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `username` | string | NotBlank, `^[a-zA-Z0-9_]{3,30}$` | 로그인 아이디 |
| `password` | string | NotBlank, 6~50자 | 평문(서버에서 BCrypt 해시) |
| `nickname` | string | NotBlank, ≤30자 | 표시 이름 |
| `email` | string | NotBlank, Email | 이메일 |
| `agreedTerms` | boolean | true 필수 | 약관 동의 |

```json
{
  "username": "echo_user",
  "password": "p@ssw0rd",
  "nickname": "에코",
  "email": "user@example.com",
  "agreedTerms": true
}
```

- 응답 데이터: `TokenResponse`(§5.1).
- 주요 에러: `VALIDATION_FAILED`, `USERNAME_DUPLICATED`, `EMAIL_DUPLICATED`.

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresInSec": 86400,
    "user": {
      "id": 1,
      "username": "echo_user",
      "email": "user@example.com",
      "nickname": "에코",
      "streak": 0,
      "exp": 0,
      "createdAt": "2026-05-05T14:23:11.123Z"
    }
  }
}
```

#### `POST /api/auth/login`
- 목적: 아이디/비밀번호 로그인.
- 요청 본문: `LoginRequest { username: string, password: string }` (둘 다 NotBlank).
- 응답 데이터: `TokenResponse`(§5.1).
- 주요 에러: `LOGIN_FAILED`(401), `VALIDATION_FAILED`.

#### `POST /api/auth/check-username`
- 목적: 회원가입 전 아이디 사용 가능 여부 확인.
- 요청 본문: `CheckRequest { value: string }` (NotBlank).
- 응답 데이터: `CheckResponse { available: boolean }`. `true`면 가입 가능.

```json
// 요청
{ "value": "echo_user" }
// 응답
{ "success": true, "data": { "available": true } }
```

#### `POST /api/auth/check-email`
- 목적: 회원가입 전 이메일 사용 가능 여부 확인.
- 요청 본문: `CheckRequest { value: string }` (NotBlank).
- 응답 데이터: `CheckResponse { available: boolean }`.

#### `GET /api/auth/oauth2/google/demo`
- 목적: 데모/테스트용 가짜 Google OAuth2 로그인. 실제 Google 인증 없이 정해진 데모 사용자 토큰을 발급.
- 요청 파라미터: 없음.
- 응답 데이터: `TokenResponse`(§5.1).

---

### 4.3 Member (`/api/members`)

#### `GET /api/members/me`
- 목적: 로그인 사용자의 프로필 조회.
- 인증: JWT 필수.
- 응답 데이터: `UserResponse`(§5.2).

#### `PATCH /api/members/me/nickname`
- 목적: 닉네임 변경.
- 인증: JWT 필수.
- 요청 본문: `UpdateNicknameRequest { nickname: string }` (NotBlank, ≤30자).
- 응답 데이터: 변경된 `UserResponse`(§5.2).
- 주요 에러: `VALIDATION_FAILED`, `USER_NOT_FOUND`.

```json
// 요청
{ "nickname": "새닉네임" }
```

---

### 4.4 Feedback — 쓰기 (`/api/feedback`)

모두 JWT 필수.

#### `POST /api/feedback/generate`
- 목적: 한 학습 단위(스크립트 또는 사용자 세션) 종료 시점에 누적된 녹음들로 종합 피드백을 생성.
- 요청 본문: `GenerateFeedbackRequest`

| 필드 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `scriptId` | Long? | scriptId / sessionId 중 정확히 하나만 채움 | 프리셋 스크립트 단위일 때 |
| `sessionId` | Long? | 위와 동일 | 사용자 맞춤 세션 단위일 때 |
| `recordingIds` | Long[] | NotEmpty | 누적 녹음 ID 목록(이전 `POST /api/recordings` 응답의 `id`) |

```json
{
  "scriptId": 12,
  "recordingIds": [101, 102, 103, 104]
}
```

- 응답 데이터: `FeedbackResponse`(§5.4).
- 주요 에러: `INVALID_REQUEST`, `SCRIPT_NOT_FOUND`, `SESSION_NOT_FOUND`, `RECORDING_NOT_FOUND`, `MODEL_SERVER_UNAVAILABLE`, `MODEL_SERVER_ERROR`.

#### `POST /api/feedback/{feedbackId}/retry-word`
- 목적: 종합 피드백이 짚어 준 약점 단어를 다시 발음하여 즉시 재평가.
- 콘텐츠 타입: `multipart/form-data`
- 폼 파트:

| 파트 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `audio` | file | ✅ | 사용자 음성(WAV/등). 서버에서 WAV로 변환 후 모델 서버 호출 |

- 응답 데이터: `RetryWordResponse`(§5.5).
- 주요 에러: `FEEDBACK_NOT_FOUND`, `AUDIO_DECODE_FAILED`, `INVALID_REQUEST`(빈 파트/사이즈 초과), `MODEL_SERVER_*`.

```json
// 응답 예시
{
  "success": true,
  "data": {
    "correct": false,
    "perceived": ["w", "ʌ", "t", "ɚ"],
    "canonical": ["w", "ɔ", "t", "ɚ"],
    "score": 73.4,
    "guidanceKr": "ɔ 모음을 입을 더 둥글게 만들어 발음해 보세요."
  }
}
```

#### `POST /api/feedback/{feedbackId}/complete`
- 목적: 피드백 화면을 닫을 때 호출. 학습 완료를 확정하고 경험치/스트릭/배지 등 보상 처리.
- 요청 본문: 없음.
- 응답 데이터: 보상 반영된 `UserResponse`(§5.2).
- 주요 에러: `FEEDBACK_NOT_FOUND`.

---

### 4.5 Feedback — 조회 (`/api/feedbacks`)

> 경로가 복수형(`feedbacks`)입니다. 쓰기는 단수형 `/api/feedback`. 프론트 의존성 보존을 위해 의도적으로 분리되어 있습니다.

#### `GET /api/feedbacks`
- 목적: 본인 피드백 목록.
- 응답 데이터: `FeedbackSummaryResponse[]`(§5.6).

```json
{
  "success": true,
  "data": [
    {
      "id": 31,
      "title": "Daily Conversation #4",
      "accuracy": 84.3,
      "weakPhoneme": "ɔ",
      "createdAt": "2026-05-05T13:10:00Z"
    }
  ]
}
```

#### `GET /api/feedbacks/{feedbackId}`
- 목적: 피드백 상세.
- 응답 데이터: `FeedbackResponse`(§5.4).
- 주요 에러: `FEEDBACK_NOT_FOUND`.

---

### 4.6 Recording (`/api/recordings`)

#### `POST /api/recordings`
- 목적: 학습 단계 한 번의 녹음을 업로드 → 즉시 음소 분석 결과를 반환.
- 인증: JWT 필수.
- 응답 상태: `201 Created`.
- 콘텐츠 타입: `multipart/form-data`
- 폼 파트/파라미터:

| 파트/파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| `audio` | form-data part | file | ✅ | 사용자 음성 |
| `scriptId` | query | Long | 선택 | 프리셋 스크립트 컨텍스트 |
| `sessionId` | query | Long | 선택 | 사용자 세션 컨텍스트 |
| `stepId` | query | Long | 선택 | 스크립트 학습 단계 컨텍스트 |
| `sessionSentenceId` | query | Long | 선택 | 세션 문장 컨텍스트 |

> 컨텍스트 파라미터는 모두 선택이며, 학습 화면 종류에 따라 채워지는 조합이 달라집니다(스크립트 학습이면 `scriptId`+`stepId`, 사용자 세션이면 `sessionId`+`sessionSentenceId`).

- 응답 데이터: `RecordingResponse`(§5.3).
- 주요 에러: `AUDIO_DECODE_FAILED`, `INVALID_REQUEST`, `SCRIPT_NOT_FOUND`, `SESSION_NOT_FOUND`, `STEP_NOT_FOUND`, `SESSION_SENTENCE_NOT_FOUND`, `MODEL_SERVER_UNAVAILABLE`, `MODEL_SERVER_ERROR`.

```json
// 응답 예시 (요약)
{
  "success": true,
  "data": {
    "id": 101,
    "scriptId": 12,
    "sessionId": null,
    "stepId": 73,
    "sessionSentenceId": null,
    "durationSec": 4.21,
    "perceived": ["w", "ʌ", "t", "ɚ"],
    "canonical": ["w", "ɔ", "t", "ɚ"],
    "peakSoftmax": [0.91, 0.62, 0.88, 0.79],
    "stepScore": 78.0,
    "guidanceKr": "ɔ 모음을 더 둥글게.",
    "errors": [
      { "op": "substitution", "canonical": "ɔ", "perceived": "ʌ", "canonicalIndex": 1 }
    ],
    "wrongWords": [{ "word": "water", "index": 3 }],
    "createdAt": "2026-05-05T13:08:21.450Z"
  }
}
```

---

### 4.7 Session (`/api/sessions`)

사용자 맞춤 스크립트 세션 CRUD. 모두 JWT 필수.

#### `GET /api/sessions`
- 응답 데이터: `SessionResponse[]`(§5.7).

#### `POST /api/sessions`
- 응답 상태: `201 Created`.
- 요청 본문: `SessionCreateRequest { title: string }` (NotBlank, ≤100자).
- 응답 데이터: 생성된 `SessionResponse`(§5.7).

```json
{ "title": "내 발음 연습 5/5" }
```

#### `GET /api/sessions/{sessionId}`
- 응답 데이터: `SessionResponse`(§5.7).
- 주요 에러: `SESSION_NOT_FOUND`.

#### `PATCH /api/sessions/{sessionId}`
- 목적: 부분 수정. `null` 필드는 변경하지 않음.
- 요청 본문: `SessionUpdateRequest`

| 필드 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `title` | string? | ≤100자 | 제목 수정 |
| `scriptText` | string? | ≤5000자 | 사용자 입력 원문(서버가 문장 단위로 분할 저장) |
| `favorite` | boolean? | — | 즐겨찾기 토글 |

- 응답 데이터: `SessionResponse`(§5.7).
- 주요 에러: `SESSION_NOT_FOUND`, `VALIDATION_FAILED`.

```json
{ "favorite": true }
```

#### `DELETE /api/sessions/{sessionId}`
- 응답 데이터: 없음(`success: true`만).
- 주요 에러: `SESSION_NOT_FOUND`.

---

### 4.8 Script (`/api/scripts`)

JWT 필수.

#### `GET /api/scripts/recommended/today`
- 목적: 오늘의 추천 스크립트 목록.
- 응답 데이터: `ScriptSummaryResponse[]`(§5.8).

```json
{
  "success": true,
  "data": [
    { "id": 12, "title": "Coffee Shop", "difficulty": "EASY", "isPreset": true }
  ]
}
```

#### `GET /api/scripts/{scriptId}`
- 목적: 스크립트 상세 + 학습 단계 흐름.
- 응답 데이터: `ScriptDetailResponse`(§5.9).
- 주요 에러: `SCRIPT_NOT_FOUND`.

---

### 4.9 Track (`/api/tracks`)

JWT 필수.

#### `GET /api/tracks`
- 응답 데이터: `TrackSummaryResponse[]`(§5.10).

#### `GET /api/tracks/{trackId}`
- 응답 데이터: `TrackDetailResponse`(§5.11).
- 주요 에러: `TRACK_NOT_FOUND`.

---

### 4.10 Ranking (`/api/ranking`)

JWT 필수.

#### `GET /api/ranking/today`
- 목적: 오늘 학습 단위에 대한 본인 등수와 상위권 엔트리.
- 응답 데이터: `RankingResponse`(§5.12).

```json
{
  "success": true,
  "data": {
    "unitTitle": "Coffee Shop",
    "myRank": 12,
    "totalUsers": 240,
    "myAccuracy": 78.4,
    "entries": [
      { "rank": 1, "nickname": "alice", "accuracy": 95.2, "isMe": false },
      { "rank": 12, "nickname": "에코", "accuracy": 78.4, "isMe": true }
    ]
  }
}
```

---

### 4.11 Stats (`/api/stats`)

JWT 필수.

#### `GET /api/stats/me`
- 목적: 내 통계(스트릭, 경험치, 출석, 주간 약점, 배지).
- 쿼리 파라미터:

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `year` | Integer | 선택 | 출석 캘린더 기준 연도. 미지정 시 현재 |
| `month` | Integer | 선택 | 출석 캘린더 기준 월(1~12). 미지정 시 현재 |

- 응답 데이터: `StatsResponse`(§5.13).

```json
{
  "success": true,
  "data": {
    "streak": 3,
    "exp": 1240,
    "attendance": {
      "year": 2026,
      "month": 5,
      "days": { "1": 1, "2": 2, "3": 3, "5": 1 }
    },
    "weeklyErrors": [
      { "sound": "ɔ", "count": 7 },
      { "sound": "θ", "count": 4 }
    ],
    "badges": [
      { "id": "FIRST_FEEDBACK", "name": "첫 피드백", "achieved": true }
    ]
  }
}
```

> `attendance.days`는 day(1~31) → 그 날까지의 누적 출석 streak 값 매핑입니다. 출석이 없는 날은 키 자체가 생략됩니다.

---

### 4.12 TTS (`/api/tts`)

JWT 필수. **응답이 `ApiResponse` envelope이 아닌 raw 바이너리** 입니다.

#### `POST /api/tts`
- 목적: 영문 텍스트를 합성 음성(MP3)으로 받기.
- 요청 본문: `TtsRequest`

| 필드 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `text` | string | NotBlank, ≤500자 | 합성할 텍스트 |
| `lang` | string? | — | 선택. 미지정 시 서버 기본(영어) |

```json
{ "text": "Welcome to the coffee shop." }
```

- 응답: `200 OK`, `Content-Type: audio/mpeg`, body는 MP3 바이트.
- 주요 에러: `INVALID_REQUEST`(검증 실패), `MODEL_SERVER_UNAVAILABLE`, `MODEL_SERVER_ERROR`.

> 에러 시에는 `ApiResponse<Void>` JSON으로 응답합니다(컨텐츠 협상 실패가 아닌 한). 클라이언트는 `Content-Type`을 보고 분기하세요.

---

## 5. 스키마 부록

### 5.1 `TokenResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | string | JWT |
| `tokenType` | string | 항상 `"Bearer"` |
| `expiresInSec` | long | 토큰 만료까지 남은 초 |
| `user` | `UserResponse` | 발급 시점의 사용자 스냅샷 |

### 5.2 `UserResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 사용자 ID |
| `username` | string | 로그인 아이디 |
| `email` | string | 이메일 |
| `nickname` | string | 표시 이름 |
| `streak` | int | 연속 학습 일수(최대 7) |
| `exp` | int | 누적 경험치 |
| `createdAt` | Instant | 가입 시각 |

### 5.3 `RecordingResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 녹음 ID |
| `scriptId` | Long? | 스크립트 컨텍스트 |
| `sessionId` | Long? | 세션 컨텍스트 |
| `stepId` | Long? | 학습 단계 컨텍스트 |
| `sessionSentenceId` | Long? | 세션 문장 컨텍스트 |
| `durationSec` | Double? | 음성 길이(초) |
| `perceived` | string[] | 모델이 인식한 음소 시퀀스 |
| `canonical` | string[] | 정답 음소 시퀀스(런타임 G2P) |
| `peakSoftmax` | double[] | 음소별 최고 softmax |
| `stepScore` | Double? | 0~100 점수(`ScoringPolicy`) |
| `guidanceKr` | string? | 한 줄 한글 가이드 |
| `errors` | `PhonemeErrorResponse[]` | 정렬 결과 중 오답 항목 |
| `wrongWords` | `WrongWord[]` | LLM이 짚어 준 단어(없으면 `[]`) |
| `createdAt` | Instant | 업로드 시각 |

### 5.4 `FeedbackResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 피드백 ID |
| `scriptId` | Long? | 스크립트 컨텍스트 |
| `sessionId` | Long? | 세션 컨텍스트 |
| `title` | string | 학습 단위 제목 |
| `accuracy` | double | 종합 정확도(0~100) |
| `weakPhoneme` | string | 가장 약한 음소 |
| `practiceWord` | string | 권장 연습 단어 |
| `guidanceKr` | string | 한글 가이드 |
| `errors` | `PhonemeErrorResponse[]` | 누적 오답 음소 |
| `createdAt` | Instant | 생성 시각 |

### 5.5 `RetryWordResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `correct` | boolean | 통과 여부 |
| `perceived` | string[] | 인식된 음소 |
| `canonical` | string[] | 정답 음소 |
| `score` | double | 0~100 점수 |
| `guidanceKr` | string | 한 줄 한글 가이드 |

### 5.6 `FeedbackSummaryResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 피드백 ID |
| `title` | string | 학습 단위 제목 |
| `accuracy` | double | 정확도 |
| `weakPhoneme` | string | 약점 음소 |
| `createdAt` | Instant | 생성 시각 |

### 5.7 `SessionResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 세션 ID |
| `title` | string | 제목 |
| `scriptText` | string | 사용자 원문 |
| `favorite` | boolean | 즐겨찾기 |
| `sentences` | `SessionSentenceResponse[]` | 분할된 문장(아래) |
| `createdAt` | Instant | 생성 시각 |
| `updatedAt` | Instant | 수정 시각 |

`SessionSentenceResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 문장 ID(녹음 업로드 시 `sessionSentenceId`에 그대로 사용) |
| `sentenceIndex` | int | 0-based 정렬 키 |
| `text` | string | 문장 본문 |

### 5.8 `ScriptSummaryResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 스크립트 ID |
| `title` | string | 제목 |
| `difficulty` | enum `Difficulty` | `EASY` / `MEDIUM` / `HARD` |
| `isPreset` | boolean | 프리셋 여부 |

### 5.9 `ScriptDetailResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 스크립트 ID |
| `title` | string | 제목 |
| `content` | string | 원문 |
| `difficulty` | enum `Difficulty` | EASY/MEDIUM/HARD |
| `isPreset` | boolean | 프리셋 여부 |
| `steps` | `StepResponse[]` | 학습 단계 흐름 |

`StepResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 단계 ID(녹음 업로드 시 `stepId`에 사용) |
| `orderIndex` | int | 0-based 정렬 키 |
| `kind` | enum `StepKind` | `INTRO`(안내, 녹음 없음) / `RECORD`(녹음 필요) |
| `prompt` | string | 채팅 흐름 메시지 |
| `targetText` | string | 발음할 영어 문장 |

### 5.10 `TrackSummaryResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 트랙 ID |
| `title` | string | 트랙 제목 |
| `description` | string | 설명 |
| `displayOrder` | int | 노출 순서 |
| `chapterCount` | int | 챕터 수 |

### 5.11 `TrackDetailResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 트랙 ID |
| `title` | string | 트랙 제목 |
| `description` | string | 설명 |
| `displayOrder` | int | 노출 순서 |
| `chapters` | `ChapterSummaryResponse[]` | 챕터(스크립트) 목록 |

`ChapterSummaryResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `scriptId` | Long | `GET /api/scripts/{id}` 키로 그대로 사용 |
| `chapterOrder` | int | 챕터 순서(없으면 0) |
| `title` | string | 챕터 제목 |
| `difficulty` | enum `Difficulty` | EASY/MEDIUM/HARD |

### 5.12 `RankingResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `unitTitle` | string | 학습 단위 제목 |
| `myRank` | int | 내 등수 |
| `totalUsers` | int | 참여자 수 |
| `myAccuracy` | double | 내 정확도 |
| `entries` | `Entry[]` | 상위 엔트리 |

`Entry`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `rank` | int | 순위 |
| `nickname` | string | 닉네임 |
| `accuracy` | double | 정확도 |
| `isMe` | boolean | 본인 여부 |

### 5.13 `StatsResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `streak` | int | 누적 스트릭 |
| `exp` | int | 누적 경험치 |
| `attendance` | `Attendance` | 출석 캘린더 |
| `weeklyErrors` | `PhonemeFrequency[]` | 지난 7일 약점 음소 top N |
| `badges` | `Badge[]` | 보유/미보유 배지 |

`Attendance`: `{ year: int, month: int, days: Map<int, int> }` — `days[day]` = 그 날까지의 누적 streak.
`PhonemeFrequency`: `{ sound: string, count: int }`.
`Badge`: `{ id: string, name: string, achieved: boolean }`.

### 5.14 `PhonemeErrorResponse`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `op` | string | `substitution` / `insertion` / `deletion` |
| `canonical` | string? | 정답 음소(있다면) |
| `perceived` | string? | 인식 음소(있다면) |
| `canonicalIndex` | Integer? | 정답 시퀀스에서의 위치 |

### 5.15 `WrongWord`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `word` | string | 원문 단어(소문자, 따옴표/구두점 제거) |
| `index` | int | `targetText`를 공백/구두점으로 쪼갠 단어 배열에서의 0-based 위치 |

---

## 부록 A. 에러 코드 카탈로그 (`ErrorCode`)

`com.capstoneecho.echo_back.app.common.ErrorCode`에 정의된 모든 코드와 기본 HTTP 상태/메시지입니다. 응답 `error.message`는 호출 지점에서 보강될 수 있으므로 클라이언트는 `error.code`로 분기하십시오.

| code | HTTP | defaultMessage |
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

`GlobalExceptionHandler`의 매핑 규칙:
- `BusinessException` → `error.code` = 그 예외가 갖고 있는 `ErrorCode`, HTTP = 그 코드의 status.
- `MethodArgumentNotValidException` → `VALIDATION_FAILED`(첫 필드 에러를 메시지에 포함).
- `MaxUploadSizeExceededException` → `INVALID_REQUEST`("업로드 파일 크기가 제한을 초과했습니다.").
- `MissingServletRequestPartException`/`MissingServletRequestParameterException` → `INVALID_REQUEST`.
- Spring `AuthenticationException` / `AccessDeniedException` → `UNAUTHORIZED`.
- 그 외 모든 예외 → `INTERNAL_ERROR`(서버 로그에 stack trace 기록).

---

## 부록 B. 변경 이력

| 일자 | 변경 |
| --- | --- |
| 2026-05-05 | 초안 작성. 12개 컨트롤러 / 26개 엔드포인트 / 19개 ErrorCode 수록. |

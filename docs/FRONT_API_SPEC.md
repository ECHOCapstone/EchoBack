# EchoFront API Specification

프론트엔드(`src/app/api/`)가 백엔드로 호출하는 모든 엔드포인트를 한 곳에 정리한 명세서. 모든 호출은 `src/app/api/client.ts` 의 `apiClient` 를 통과하며, 인증 / envelope 디코딩 / 에러 정규화는 클라이언트 한 곳에서만 처리된다.

본 문서의 단일 출처는 `src/app/api/*.ts` 이며, 백엔드 응답이 본 명세와 다르면 본 명세를 갱신한다.

---

## 1. Overview

### Base URL
- 런타임 base URL: `env.apiBaseUrl` (`src/app/lib/env.ts`).
- 모든 path 는 base 에 그대로 prefix 된다. 본 문서의 path 는 base 뒤에 붙는 path만 표기한다.

### 인증
- 헤더: `Authorization: Bearer <accessToken>`.
- 토큰 저장소: `localStorage['echo.accessToken']`. `setAccessToken(null)` 로 로그아웃 처리.
- 클라이언트는 토큰이 존재하면 무조건 헤더에 실어 보내므로, 백엔드 입장에서는 모든 보호 엔드포인트가 동일하게 JWT 를 기대한다고 보면 된다. `/api/auth/*`, `/api/auth/oauth2/google/demo`, `/api/tts` 등 비로그인 컨텍스트에서도 호출 가능한 엔드포인트는 토큰을 무시한다.

### Response Envelope
모든 JSON 응답은 envelope 으로 감싸진다.

```ts
type ApiEnvelope<T> =
  | { success: true;  data: T;    error: null }
  | { success: false; data: null; error: { code: string; message: string } };
```

- `success: true` → 클라이언트는 `data` 만 호출자에게 반환한다.
- `success: false` 혹은 HTTP 비-2xx → `ApiException { code, status }` 로 던진다.
- 응답 본문이 비어 있는 2xx 의 경우 클라이언트는 `undefined` 를 반환한다(예: `DELETE /api/sessions/{id}`).
- 바이너리 엔드포인트는 envelope 을 사용하지 않는다 (현재는 `POST /api/tts` 1건).

### 에러
- 백엔드가 envelope `error` 를 내려주면 그대로 사용한다.
- envelope 없이 HTTP 에러로만 떨어지면 클라이언트가 `code = "HTTP_<status>"`, `message = response.statusText` 로 폴백 생성한다.
- 바이너리 엔드포인트도 동일한 폴백 규칙을 적용한다.

---

## 2. Auth

### POST /api/auth/signup
- **Auth**: 불필요.
- **Request body** (JSON, `SignupInput`):
  ```ts
  {
    username: string;
    password: string;
    nickname: string;
    email: string;
    agreedTerms: boolean;
  }
  ```
- **Response**: `TokenResponse`.
- **Notes**: 신규 가입 즉시 로그인 토큰을 발급한다.

### POST /api/auth/login
- **Auth**: 불필요.
- **Request body** (JSON, `LoginInput`):
  ```ts
  { username: string; password: string }
  ```
- **Response**: `TokenResponse`.

### POST /api/auth/check-username
- **Auth**: 불필요.
- **Request body** (JSON):
  ```ts
  { value: string }
  ```
- **Response**: `{ available: boolean }`.
- **Notes**: 회원가입 화면 실시간 중복확인.

### POST /api/auth/check-email
- **Auth**: 불필요.
- **Request body** (JSON):
  ```ts
  { value: string }
  ```
- **Response**: `{ available: boolean }`.

### GET /api/auth/oauth2/google/demo
- **Auth**: 불필요.
- **Request body**: 없음 (no request body).
- **Query params**: 없음.
- **Response**: `TokenResponse`.
- **Notes**: 데모용 즉시 로그인 — 실제 OAuth 흐름이 아니라 백엔드에서 데모 계정 토큰을 발급하는 단축 엔드포인트.

---

## 3. Members

### GET /api/members/me
- **Auth**: 필수.
- **Request body**: 없음.
- **Query params**: 없음.
- **Response**: `User`.

### PATCH /api/members/me/nickname
- **Auth**: 필수.
- **Request body** (JSON):
  ```ts
  { nickname: string }
  ```
- **Response**: 갱신된 `User`.

---

## 4. Tracks

### GET /api/tracks
- **Auth**: 필수.
- **Request body**: 없음.
- **Query params**: 없음.
- **Response**: `TrackSummary[]`.

### GET /api/tracks/{trackId}
- **Auth**: 필수.
- **Path params**: `trackId: number`.
- **Request body**: 없음.
- **Response**: `TrackDetail`.
- **Notes**: `chapters[].scriptId` 가 `/api/scripts/{scriptId}` 호출 키로 그대로 사용된다.

---

## 5. Scripts

### GET /api/scripts/recommended/today
- **Auth**: 필수.
- **Request body**: 없음.
- **Query params**: 없음.
- **Response**: `ScriptSummary[]`.

### GET /api/scripts/{scriptId}
- **Auth**: 필수.
- **Path params**: `scriptId: number`.
- **Request body**: 없음.
- **Response**: `ScriptDetail` (`steps: LearningStep[]` 포함).

---

## 6. Sessions

사용자 맞춤(custom) 학습 세션. 세션 1건이 여러 `SessionSentence` 로 분리되며, 각 문장 단위로 녹음 업로드가 가능하다.

### GET /api/sessions
- **Auth**: 필수.
- **Request body**: 없음.
- **Response**: `Session[]`.

### POST /api/sessions
- **Auth**: 필수.
- **Request body** (JSON):
  ```ts
  { title: string }
  ```
- **Response**: 생성된 `Session`.

### GET /api/sessions/{id}
- **Auth**: 필수.
- **Path params**: `id: number`.
- **Request body**: 없음.
- **Response**: `Session`.

### PATCH /api/sessions/{id}
- **Auth**: 필수.
- **Path params**: `id: number`.
- **Request body** (JSON, partial update — 모든 필드 옵셔널):
  ```ts
  {
    title?: string;
    scriptText?: string;
    favorite?: boolean;
  }
  ```
- **Response**: 갱신된 `Session`.
- **Notes**: `favorite` 는 토글 의도를 분명히 하기 위해 boolean 으로 명시 전달한다.

### DELETE /api/sessions/{id}
- **Auth**: 필수.
- **Path params**: `id: number`.
- **Request body**: 없음.
- **Response**: `void` (응답 본문 없음).

---

## 7. Recordings

### POST /api/recordings
- **Auth**: 필수.
- **Content-Type**: `multipart/form-data`.
- **Form fields**:
  - `audio` *(required, file)* — 16-bit PCM WAV Blob. 기본 파일명은 `audio.wav`.
  - `scriptId` *(integer, optional)* — 추천 학습 모드에서 사용.
  - `stepId` *(integer, optional)* — `LearningStep.id`. `scriptId` 와 함께 사용.
  - `sessionId` *(integer, optional)* — 맞춤 학습 모드에서 사용.
  - `sessionSentenceId` *(integer, optional)* — 문장 단위 학습 키. `sessionId` 와 함께 사용.
- **상호 배타 규약**: `scriptId` / `sessionId` 중 **정확히 하나만** 전송한다.
  - 추천 학습: `{ audio, scriptId, stepId }`
  - 맞춤 학습: `{ audio, sessionId, sessionSentenceId }`
- 클라이언트는 `null` / `undefined` 인 필드를 form 에 아예 붙이지 않는다(`"null"` 문자열이 백엔드 `@RequestParam Long` 변환에서 터지는 것을 막기 위해서).
- **Response**: `RecordingResult`.

---

## 8. Feedback

### POST /api/feedback/generate
- **Auth**: 필수.
- **Request body** (JSON, `GenerateFeedbackInput`):
  ```ts
  {
    scriptId?: number;
    sessionId?: number;
    recordingIds: number[];
  }
  ```
- **Response**: `Feedback`.
- **Notes**: `scriptId` / `sessionId` 중 한쪽만 채워서 보낸다(녹음과 동일 규약).

### POST /api/feedback/{feedbackId}/retry-word
- **Auth**: 필수.
- **Path params**: `feedbackId: number`.
- **Content-Type**: `multipart/form-data`.
- **Form fields**:
  - `audio` *(required, file)* — 단일 단어 재시도 녹음. 기본 파일명 `audio.wav`.
- **Response**: `RetryWordResult`.

### POST /api/feedback/{feedbackId}/complete
- **Auth**: 필수.
- **Path params**: `feedbackId: number`.
- **Request body**: 없음 (no request body).
- **Response**: 갱신된 `User` (EXP / streak 보상 반영).

### GET /api/feedbacks
- **Auth**: 필수.
- **Request body**: 없음.
- **Response**: `FeedbackSummary[]`.

### GET /api/feedbacks/{id}
- **Auth**: 필수.
- **Path params**: `id: number`.
- **Request body**: 없음.
- **Response**: `Feedback`.

---

## 9. Stats

### GET /api/stats/me
- **Auth**: 필수.
- **Request body**: 없음.
- **Query params** (모두 옵셔널 — 함께 보내야 의미가 있음):
  - `year: number`
  - `month: number`
- **Response**: `Stats`.
- **Notes**: `year` / `month` 미지정 시 백엔드는 오늘이 속한 월의 출석을 반환한다.

---

## 10. Ranking

### GET /api/ranking/today
- **Auth**: 필수.
- **Request body**: 없음.
- **Query params**: 없음.
- **Response**: `Ranking`.

---

## 11. TTS

### POST /api/tts
- **Auth**: 불필요(클라이언트 호출 시점에 토큰이 있으면 함께 보내지만 백엔드는 무시 가능).
- **Request body** (JSON):
  ```ts
  { text: string; lang?: string /* default 'en' */ }
  ```
- **Response**: **바이너리** `audio/mpeg` Blob (envelope 미사용).
- **Notes**: 호출자는 응답 Blob 을 `URL.createObjectURL` 로 변환해 `<audio>` 에 연결한다. 에러 시에도 envelope 없이 HTTP 상태로만 떨어지며, 클라이언트가 `HTTP_<status>` 폴백 에러를 생성한다.

---

## 12. DTO Appendix

`src/app/api/types.ts` 의 모든 타입을 한 곳에 모은다. 필드 타입은 TypeScript 표기를 그대로 옮겼다.

### Primitives
- `Difficulty = 'EASY' | 'MEDIUM' | 'HARD'`
- `StepKind = 'INTRO' | 'RECORD'`
- `ApiError = { code: string; message: string }`
- `ApiEnvelope<T>` — 위 Overview 참고.

### User / Auth
- **`User`**
  - `id: number`, `username: string`, `email: string`, `nickname: string`
  - `streak: number`, `exp: number`, `createdAt: string`
- **`TokenResponse`**
  - `accessToken: string`, `tokenType: string`, `expiresInSec: number`, `user: User`

### Scripts
- **`ScriptSummary`**: `id: number`, `title: string`, `difficulty: Difficulty`, `isPreset: boolean`
- **`LearningStep`**: `id: number`, `orderIndex: number`, `kind: StepKind`, `prompt: string`, `targetText: string | null`
- **`ScriptDetail`**: `id`, `title`, `content: string`, `difficulty: Difficulty`, `isPreset: boolean`, `steps: LearningStep[]`

### Sessions
- **`SessionSentence`**: `id: number`, `sentenceIndex: number`, `text: string`
- **`Session`**: `id`, `title`, `scriptText: string`, `favorite: boolean`, `sentences: SessionSentence[]`, `createdAt: string`, `updatedAt: string`

### Recording / Feedback
- **`WrongWord`**: `word: string`, `index: number` *(targetText 의 단어 중 0-based)*
- **`PhonemeError`**: `op: string`, `canonical: string | null`, `perceived: string | null`, `canonicalIndex: number | null`
- **`RecordingResult`**:
  - `id: number`
  - `scriptId: number | null`, `sessionId: number | null`, `stepId: number | null`, `sessionSentenceId: number | null`
  - `durationSec: number | null`
  - `perceived: string[]`, `canonical: string[]`, `peakSoftmax: number[]`
  - `stepScore: number | null`, `guidanceKr: string | null`
  - `errors: PhonemeError[]`, `wrongWords: WrongWord[]`
  - `createdAt: string`
- **`Feedback`**:
  - `id: number`, `scriptId: number | null`, `sessionId: number | null`
  - `title: string`, `accuracy: number`
  - `weakPhoneme: string | null`, `practiceWord: string | null`, `guidanceKr: string | null`
  - `errors: PhonemeError[]`, `createdAt: string`
- **`FeedbackSummary`**: `id`, `title`, `accuracy`, `weakPhoneme: string | null`, `createdAt`
- **`RetryWordResult`**: `correct: boolean`, `perceived: string[]`, `canonical: string[]`, `score: number`, `guidanceKr: string`

### Stats / Ranking
- **`Stats`**:
  - `streak: number`, `exp: number`
  - `attendance: { year: number; month: number; days: Record<string, number> }`
  - `weeklyErrors: { sound: string; count: number }[]`
  - `badges: { id: string; name: string; achieved: boolean }[]`
- **`Ranking`**:
  - `unitTitle: string`, `myRank: number`, `totalUsers: number`, `myAccuracy: number`
  - `entries: { rank: number; nickname: string; accuracy: number; isMe: boolean }[]`

### Tracks
- **`TrackSummary`**: `id: number`, `title: string`, `description: string`, `displayOrder: number`, `chapterCount: number`
- **`ChapterSummary`**: `scriptId: number`, `chapterOrder: number`, `title: string`, `difficulty: Difficulty`
- **`TrackDetail`**: `id`, `title`, `description`, `displayOrder`, `chapters: ChapterSummary[]`

---

## 13. Endpoint Index (Quick Reference)

| # | Method | Path | Auth | Request | Response |
|---|---|---|---|---|---|
| 1 | POST | `/api/auth/signup` | – | `SignupInput` JSON | `TokenResponse` |
| 2 | POST | `/api/auth/login` | – | `LoginInput` JSON | `TokenResponse` |
| 3 | POST | `/api/auth/check-username` | – | `{ value }` JSON | `{ available }` |
| 4 | POST | `/api/auth/check-email` | – | `{ value }` JSON | `{ available }` |
| 5 | GET  | `/api/auth/oauth2/google/demo` | – | – | `TokenResponse` |
| 6 | GET  | `/api/members/me` | ✓ | – | `User` |
| 7 | PATCH | `/api/members/me/nickname` | ✓ | `{ nickname }` JSON | `User` |
| 8 | GET  | `/api/tracks` | ✓ | – | `TrackSummary[]` |
| 9 | GET  | `/api/tracks/{trackId}` | ✓ | – | `TrackDetail` |
| 10 | GET | `/api/scripts/recommended/today` | ✓ | – | `ScriptSummary[]` |
| 11 | GET | `/api/scripts/{scriptId}` | ✓ | – | `ScriptDetail` |
| 12 | GET | `/api/sessions` | ✓ | – | `Session[]` |
| 13 | POST | `/api/sessions` | ✓ | `{ title }` JSON | `Session` |
| 14 | GET | `/api/sessions/{id}` | ✓ | – | `Session` |
| 15 | PATCH | `/api/sessions/{id}` | ✓ | partial `Session` JSON | `Session` |
| 16 | DELETE | `/api/sessions/{id}` | ✓ | – | `void` |
| 17 | POST | `/api/recordings` | ✓ | multipart (`audio` + ids) | `RecordingResult` |
| 18 | POST | `/api/feedback/generate` | ✓ | `GenerateFeedbackInput` JSON | `Feedback` |
| 19 | POST | `/api/feedback/{feedbackId}/retry-word` | ✓ | multipart (`audio`) | `RetryWordResult` |
| 20 | POST | `/api/feedback/{feedbackId}/complete` | ✓ | – | `User` |
| 21 | GET | `/api/feedbacks` | ✓ | – | `FeedbackSummary[]` |
| 22 | GET | `/api/feedbacks/{id}` | ✓ | – | `Feedback` |
| 23 | GET | `/api/stats/me` | ✓ | query `year?`, `month?` | `Stats` |
| 24 | GET | `/api/ranking/today` | ✓ | – | `Ranking` |
| 25 | POST | `/api/tts` | – | `{ text, lang? }` JSON | `audio/mpeg` Blob |

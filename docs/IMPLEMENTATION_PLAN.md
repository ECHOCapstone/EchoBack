# IMPLEMENTATION PLAN — FRONT_API_SPEC contract alignment

`fix/api-align-to-front` 브랜치의 **기존 코드 변경** 실행 문서. 그린필드 → Phase 6 (Hardening) 로드맵은 [`docs/legacy/IMPLEMENTATION_PLAN.md`](./legacy/IMPLEMENTATION_PLAN.md) 로 archive 됨 (2026-05-13). 본 문서는 그 이후 단일 변경 — **백엔드 DTO/엔드포인트를 `docs/FRONT_API_SPEC.md` 의 contract 와 정렬** — 을 다룬다.

> 📌 **작성 원칙**
> - `/springboot-tdd` 강제: 새 spec 기준으로 기존 테스트가 RED → 구현/테스트 갱신 → GREEN. spec 변경이 이미 완료된 상태이므로 spec 자체는 본 PR 에서 추가 수정 없음.
> - 권위 문서 3종 (`API_SPEC_REFINED.md`, `COMPONENTS_REFINED.md`, `API_TEST_PLAN.md`) 와 본 문서의 §B Codex appendix 가 합의한 11개 delta 만 다룬다.
> - 무관한 cleanup/리팩토링 금지. PR diff 표면을 좁게.

---

## 1. 배경

직전 두 커밋이 권위 문서를 FRONT_API_SPEC.md 와 정렬했다:

- `152ce27 docs: align API_SPEC_REFINED + API_TEST_PLAN with FRONT_API_SPEC`
- `a0520dd docs: align COMPONENTS_REFINED with API_SPEC_REFINED rev6`

세 spec 모두 "백엔드 코드 gap" 인라인 노트로 어긋남을 명시. 2026-05-13 의 Codex adversarial review 가 추가로 8개 DTO/응답 contract 불일치를 발견 (본문 §B 부록). 본 PR 은 11개 delta 를 모두 코드에 반영한다.

### 1.1 권위 입력 문서

| 문서 | rev | 핵심 변경 |
|---|---|---|
| `docs/FRONT_API_SPEC.md` | (단일판) | 프론트 source-of-truth — 외부 입력 |
| `docs/API_SPEC_REFINED.md` | rev6 (2026-05-13) | `/api/tts` 공개, recording 컨텍스트 ID = form-data field, 2-mode 계약 |
| `docs/COMPONENTS_REFINED.md` | rev7 (2026-05-13) | SecurityConfig + RecordingService skeleton + TtsController 헤더 정렬 |
| `docs/API_TEST_PLAN.md` | rev6 (2026-05-13) | TTS dispatch 제거, Recording form 필드, free-form 시나리오 삭제 |
| `docs/ENTITIES_REFINED.md` | — | §2.7 Recording 정적 팩토리 3종 → 2종 (Task 103 에서 정렬) |

### 1.2 다루지 않는 것

- 그린필드 Phase 0~6 작업 (archived plan 참조)
- 모델 서버 / TTS provider 자체의 변경
- 새 도메인 / 새 엔드포인트 추가
- 프론트엔드 코드 / 스펙 변경 (backend 가 FRONT_API_SPEC 에 정렬)

---

## 2. 변경 범위 — 11 deltas

| ID | Source | Sev | 핵심 |
|---|---|---|---|
| D1 | rev6 | high | `/api/tts` 공개 (was JWT 필수) |
| D2 | rev6 | verify | recording context ids = multipart form fields (코드 변경 없음 — `@RequestParam` 이 자연 호환) |
| D3 | rev6 | high | `SESSION_FREE_FORM` 모드 제거 → 2-mode 계약 |
| **D4** | Codex | **critical** | `ApiResponse` envelope 형태: `{ success, data, error }` 중첩, 평탄 `errorCode`/`errorMessage` 제거 |
| **D5** | Codex | **critical** | `TokenResponse`: `accessToken`, `tokenType`, `expiresInSec`, `user` (현재 `token`/`expiresAt`/`profile`) |
| D6 | Codex | high | `LoginRequest`: `{ username, password }` (현재 `usernameOrEmail`) |
| D7 | Codex | high | check-username + check-email: `{ value }` (현재 `{ username }` / `{ email }`) |
| D8 | Codex | high | `TtsRequest`: `{ text, lang? }` (현재 `locale`) + `TtsService` 가 `lang` 소비 |
| D9 | Codex | high | feedback `retry-word` → `RetryWordResult`; `complete` → `User` (현재 둘 다 `FeedbackDetailResponse`) |
| D10 | Codex | high | `RecordingResponse.wrongWords` = `WrongWord[]` (현재 `List<String>`) |
| D11 | Codex | high | `GET /api/sessions` → 전체 `Session[]` (현재 summary 만, scriptText + sentences 누락) |
| D12 | Codex | high | Script/Track DTO 필드명: `isPreset` (현재 `preset`), `chapterCount`/`displayOrder` 누락 |

### 2.1 결정된 정책 (locked 2026-05-13)

1. **D11 session-list shape** → FRONT 정렬 (전체 `Session[]` 반환). perf 영향은 post-merge 재평가.
2. **D4 envelope NON_NULL** → FRONT §1 type union 우선 — envelope `data` / `error` 키는 항상 존재 (하나는 null). `@JsonInclude(NON_NULL)` 은 envelope 내부 DTO 필드에만 적용.
3. **명명 정책** → `@JsonAlias` 회피, 백엔드 필드명을 FRONT 와 일치하도록 rename.

---

## 3. 코드 변경 (Ralph task ↔ 파일)

### Task 100 — `envelope-and-error-shape` (D4)

- `src/main/java/.../global/common/ApiResponse.java`: 평탄 `errorCode`/`errorMessage` 두 필드 제거 → 중첩 `error: { code, message }`. 정적 팩토리 `success(T)` / `failure(ErrorCode, String)` API 유지. 클래스에 적용된 `@JsonInclude(NON_NULL)` 제거 (envelope 키 omit 금지). DTO 필드에는 NON_NULL 정책 유지.
- `src/main/java/.../global/common/GlobalExceptionHandler.java`: payload 구성에서 새 `error` nested record 사용.
- **테스트 sweep**: 모든 controller 테스트의 `jsonPath("$.errorCode")` / `$.errorMessage` 를 `$.error.code` / `$.error.message` 로 갱신. 성공 케이스에 `jsonPath("$.error").value(nullValue())` assertion 추가 가능 (선택).

### Task 101 — `auth-dto-alignment` (D5+D6+D7)

- `src/main/java/.../member/dto/AuthTokenResponse.java`: 필드명 `token`→`accessToken`, `expiresAt`→`expiresInSec` (long), `profile`→`user`, 추가 `tokenType`(="Bearer"). 정적 팩토리 갱신.
- `src/main/java/.../member/dto/LoginRequest.java`: `usernameOrEmail` → `username` (NotBlank). 검증 어노테이션 유지.
- check-username/email DTO: 단일 필드 `value` (NotBlank). 클래스명 (`UsernameCheckRequest` 등) 은 spec 외이므로 임의.
- `AuthService` 및 controller 호출부: 새 필드명 반영.

### Task 102 — `tts-public-and-lang` (D1+D8)

- `SecurityConfig.authorizeHttpRequests`: `permitAll` matcher 첫 줄에 `"/api/tts"` 추가.
- `TtsController.synthesize`: `@CurrentUser JwtPrincipal principal` 파라미터 + 그 import 2개 제거.
- `TtsRequest`: 필드 `locale` → `lang` (rename + NotBlank/Size 유지).
- `TtsService.synthesize(req)`: `req.lang()` 으로 소비. `null` 일 경우 서버 기본 (영어).

### Task 103 — `recording-remove-free-form` (D3 + D2 verify)

- `RecordingService.detectMode`: `if (!hasScript && !hasStep && hasSession && !hasSentence) { return Mode.SESSION_FREE_FORM; }` 분기 삭제. `sessionId`-only 케이스는 `INVALID_REQUEST` 로 떨어진다.
- `RecordingService.Mode`: enum 값 `SESSION_FREE_FORM` 제거. 2-value enum 만 유지.
- `RecordingService.resolveParents()` + `buildRecording()`: 각각의 `case SESSION_FREE_FORM ->` 가지 삭제. switch expression exhaustive (default 금지).
- `Recording.forSessionFreeForm(...)` 정적 팩토리 삭제.
- `ENTITIES_REFINED.md §2.7`: factory 목록에서 `forSessionFreeForm` 제거 + 개정 이력 한 줄.
- D2 verify: `RecordingControllerTest` 가 `.param("scriptId", ...)` 형태로 form 필드를 전송하므로 추가 코드 변경 없이 contract 호환.

### Task 104 — `recording-wrong-words` (D10)

- `src/main/java/.../pronunciation/recording/dto/WrongWord.java` (신규): record `WrongWord(String word, int index)`.
- `RecordingUploadResponse.wrongWords`: `List<String>` → `List<WrongWord>`.
- `RecordingService.buildOutcome`: LLM/룰 기반 결과에서 `WrongWord(word, index)` 리스트 구성. `errors.canonicalIndex` + `targetText` 단어 경계 역산으로 index 산출 (rule-based 폴백). 빈 결과는 `List.of()`.

### Task 105 — `feedback-response-shapes` (D9)

- `src/main/java/.../pronunciation/feedback/dto/RetryWordResult.java` (신규): record `{ correct: boolean, perceived: String[], canonical: String[], score: double, guidanceKr: String }` (정확히 5 필드, FRONT_API_SPEC §12 `RetryWordResult` 정합).
- `FeedbackController.retryWord`: 반환 타입 `ApiResponse<FeedbackDetailResponse>` → `ApiResponse<RetryWordResult>`. 서비스가 retry 분석 결과만 반환하도록 정리 (저장 없음 — read-only retry).
- `FeedbackController.complete`: 반환 타입 → `ApiResponse<UserResponse>` (101 의 `User` shape). `FeedbackService.complete` 가 보상 가산 후 갱신된 `UserResponse` 반환.
- 의존: 101 의 `User` DTO shape 필요.

### Task 106 — `session-list-response` (D11)

- `SessionController.list`: `List<SessionSummaryResponse>` → `List<SessionResponse>` (전체 spec-shape, scriptText + sentences 포함).
- `SessionService.listMine`: 매핑을 detail DTO 로 변경. 기존 detail mapping helper 재사용.
- `SessionSummaryResponse` DTO: 사용처 없어지면 삭제 (다른 사용처 확인 후).
- perf: list 시 `sentences` eager fetch. JOIN FETCH 또는 N+1 회피를 위해 `SessionRepository.findByUser_IdOrderByFavoriteDescUpdatedAtDesc` 에 `@EntityGraph(attributePaths="sentences")` 추가.

### Task 107 — `script-track-dto-fields` (D12)

- `ScriptSummaryResponse`: 필드 `preset` → `isPreset` (boolean). JSON 직렬화 시 `isPreset` 이 그대로 키로 나오도록 record 컴포넌트명 통일.
- `ScriptDetailResponse`: 동일.
- `TrackSummaryResponse`: 누락된 `chapterCount` 필드 확인/추가. `displayOrder` 노출 확인.
- `TrackDetailResponse`: spec 의 모든 필드 (`id`, `title`, `description`, `displayOrder`, `chapters`) 확인.
- `ChapterSummaryResponse`: spec 의 모든 필드 (`scriptId`, `chapterOrder`, `title`, `difficulty`) 확인.

---

## 4. 테스트 변경 패턴

| Task | 주요 테스트 갱신 |
|---|---|
| 100 | 모든 `*ControllerTest` 의 envelope 실패 assertion sweep (`$.errorCode` → `$.error.code` 등) |
| 101 | `AuthControllerTest` 의 signup/login/oauth-demo 응답 필드명 갱신. `UsernameCheckRequest` 시리얼라이즈 페이로드를 `{"value":"..."}` 로 |
| 102 | `TtsControllerTest`: Bearer 헤더 제거 + `missingTokenReturns401` 삭제. `SecurityConfigIntegrationTest.postTtsIsPermitAll` 추가. `TtsRequest` JSON 페이로드 `lang` 사용 |
| 103 | `RecordingControllerTest.sessionFreeFormReturns201` → `missingSessionSentenceReturns400`. `RecordingFactoryTest` 의 free-form 2개 삭제. `FeedbackServiceTest.generateCrossContextRejectsScriptAndSession` fixture 교체 + `orphanRecordingsAreAggregatedSuccessfully` 삭제 |
| 104 | `RecordingControllerTest` 응답 assertion: `$.data.wrongWords[0].word` / `$.data.wrongWords[0].index` |
| 105 | `FeedbackControllerTest` retry-word: `$.data.correct` / `$.data.score` / `$.data.guidanceKr`. complete: `$.data.streak` / `$.data.exp` / `$.data.username` |
| 106 | `SessionControllerTest.list` 응답에 `scriptText` + `sentences[].text` 검증 추가 |
| 107 | Script/Track 컨트롤러 테스트: `$.data.isPreset` / `$.data[0].chapterCount` / `$.data.chapters[0].chapterOrder` 등 |

`./gradlew test` 는 **pre-commit 훅 자동 실행**. agent 가 직접 호출하지 않는다.

---

## 5. 검증 절차

각 task 별:

1. **컴파일** — `./gradlew compileJava compileTestJava` (agent 가 직접 실행 가능, 테스트 아님).
2. **pre-commit 훅 통과** — `./gradlew test` 는 commit 시점에 hook 이 자동 실행. 모든 controller / 서비스 / 엔티티 테스트 그린.
3. **REST Docs** — `./gradlew asciidoctor` 후 `build/generated-snippets/` 가 새 contract 와 일치.
4. **회귀 grep** — 해당 task 의 acceptance 표 참조.

전체 끝난 후:

5. **Cross-spec grep**:
   - `rg -n "errorCode\\|errorMessage" src/main` = 0 (D4 잔재 없음)
   - `rg -n "SESSION_FREE_FORM\\|forSessionFreeForm" src/main` = 0 (D3)
   - `rg -n "List<String> wrongWords" src/main` = 0 (D10)
   - `rg -n "preset\\b" src/main/java/.../learning/script/dto` 0 (D12 — `isPreset` 만 남음)
6. **수동 smoke** — `./gradlew bootRun` + curl/Postman 으로 login → list-sessions → record → feedback-generate → complete → tts 흐름. 응답이 FRONT_API_SPEC §12 DTO Appendix 와 정확히 round-trip.

---

## 6. PR 구성

8 commits (task 100..107 순서), 단일 PR 제목:

```
feat(api): align backend DTOs to FRONT_API_SPEC (rev6 D1-D3 + Codex 2026-05-13 D4-D12)
```

PR body 는 §2 변경 범위 표 인용. `application-local.yaml` 의 사전 변경은 본 PR 스코프 외 — 제외.

분할 옵션 (선택):
- PR 1 — foundation (task 100 만): envelope shape + 테스트 sweep.
- PR 2 — per-domain DTOs (task 101–107): 나머지 7개.

---

## §A. 부록 — 인용 매트릭스 (delta ↔ spec 위치)

| Delta | FRONT_API_SPEC | API_SPEC_REFINED | COMPONENTS_REFINED | API_TEST_PLAN |
|---|---|---|---|---|
| D1 | §11 | §2.1, §4.12 | §2.5, §3.3.4 | §1.5.1, §3.26 |
| D2 | §7 | §4.6 | §3.3.1 | §1.7, §3.18 |
| D3 | §7 | §4.6 | §3.3.1 | §3.18 |
| D4 | §1, §1.3 | §1.1, §1.2 | §2.1 | §2.1 |
| D5 | §12 TokenResponse | §5.1 | §3.1.5 | §3.2 |
| D6 | §2.2 LoginInput | §4.2 | §3.1 | §3.3 |
| D7 | §2.3/§2.4 CheckRequest | §4.2 | §3.1 | §3.4, §3.5 |
| D8 | §11, §12 TtsRequest | §4.12 | §3.3.4 | §3.26 |
| D9 | §8 | §4.4 | §3.3.2/§3.3.3 | §3.20, §3.21 |
| D10 | §12 WrongWord, RecordingResult | §5.3, §5.15 | §3.3.1 | §3.18 |
| D11 | §6, §12 Session | §4.7 | §3.2.3 | §3.13 |
| D12 | §5, §12 Script/Track | §4.8, §4.9 | §3.2.1/§3.2.2 | §3.9-§3.12 |

---

## §B. 부록 — Codex 2026-05-13 review 결과 (verbatim)

본 PR 의 D4-D12 트리거. 권위 source.

> Target: working tree diff
> Verdict: needs-attention
>
> No ship: the working tree is documentation-only, while the live API still has multiple DTO field-name/type mismatches against FRONT_API_SPEC across core endpoints.
>
> Findings:
> - [critical] JSON envelope does not match the frontend contract (`global/common/ApiResponse.java:6-10`) — FRONT_API_SPEC defines every JSON response as `{ success, data, error }`. Backend serializes `errorCode` / `errorMessage`, `@JsonInclude(NON_NULL)` omits null fields on success. → **D4**
> - [critical] Auth token response cannot be consumed as TokenResponse (`member/dto/AuthTokenResponse.java:5-8`) — returns `token`, `expiresAt`, `profile` instead of `accessToken`, `tokenType`, `expiresInSec`, `user`. → **D5**
> - [high] Auth request DTOs reject documented frontend payloads (`member/dto/LoginRequest.java:6-13`) — binds `usernameOrEmail`, availability endpoints bind `username` / `email` instead of `value`. → **D6 + D7**
> - [high] TTS language field is still `locale`, not `lang` (`pronunciation/tts/dto/TtsRequest.java:3`) — TtsService reads `request.locale()`, frontend `lang` ignored. → **D8**
> - [high] Feedback mutation responses use the wrong DTOs (`pronunciation/feedback/controller/FeedbackController.java:39-54`) — retry-word + complete both return `ApiResponse<FeedbackDetailResponse>` instead of `RetryWordResult` / `User`. → **D9**
> - [high] Recording response `wrongWords` has the wrong element type (`pronunciation/recording/dto/RecordingUploadResponse.java:7-21`) — `List<String>` instead of `WrongWord[]` with `{ word, index }`. → **D10**
> - [high] Session list endpoint returns summaries, not `Session[]` (`learning/session/controller/SessionController.java:34-36`) — `SessionSummaryResponse` missing `scriptText` + `sentences`. → **D11**
> - [high] Script and track DTO field names are still off-spec (`learning/script/dto/ScriptSummaryResponse.java:6-10`) — `preset` instead of `isPreset`, missing `chapterCount` / `displayOrder`. → **D12**

---

## §C. 개정 이력

| 일자 | 항목 |
|---|---|
| 2026-05-13 | 초판. 그린필드 plan archive 후 FRONT_API_SPEC contract 정렬 단일 변경을 다룬다. 11개 delta (D1-D3 rev6 + D4-D12 Codex). |

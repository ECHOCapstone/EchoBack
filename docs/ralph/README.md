# Ralph Loop — ECHO Backend FRONT_API_SPEC Alignment Tasks

`docs/IMPLEMENTATION_PLAN.md` (FRONT_API_SPEC contract 정렬 plan) 을 Ralph Loop 가 iteration 단위로 처리할 수 있도록 독립 task 로 분할한 작업 폴더다. 그린필드 Phase 0~6 작업의 34개 완료된 task 는 [`docs/legacy/ralph/`](../legacy/ralph/) 로 archive 됨 (2026-05-13).

## 폴더 구성

| 파일 | 역할 |
|---|---|
| `STATUS.md` | 종료 신호 전용. 단일 토큰 `IN PROGRESS` 또는 `ALL DONE`. 후행 LF 1개. 그 외 문자 금지. |
| `PROGRESS.md` | 진행 추적 테이블. 본 시리즈 row + Last updated 타임스탬프. |
| `NNN.<kebab-title>.md` | 개별 task 명세. 100~ (rev6 + Codex 정렬 시리즈). |

## 한 iteration 절차

1. `Read docs/ralph/STATUS.md`. 내용이 정확히 `ALL DONE` 이면 루프 종료.
2. `Read docs/ralph/PROGRESS.md`. `Status=TODO` 이고 모든 `Depends` 의 ID 가 `DONE` 상태인 가장 작은 ID 를 선택. 해당 row 의 `Status` 를 `IN_PROGRESS` 로 갱신.
3. `Read docs/ralph/<선택ID>.*.md`. `## Goal` / `## Prerequisites` / `## Acceptance Criteria` 확인.
4. `springboot-tdd` 스킬에 따라 RED → GREEN → REFACTOR.
   - 컨트롤러 작업이면 MockMvc + Spring REST Docs.
   - 서비스 도메인 불변식 작업이면 `@SpringBootTest` + `@Transactional`.
   - `@MockitoBean` (Spring Framework 6.2 `org.springframework.test.context.bean.override.mockito.MockitoBean`) 으로 외부 어댑터 격리.
5. `## Acceptance Criteria` 의 모든 체크박스 충족 확인.
6. `agent/COMMIT_CONVENTIONS.md` 규약대로 `git commit`. 메시지는 task 파일의 `## Commit Message Template` 참고.
   - **테스트 실행 정책 (중요):** `./gradlew test` 는 본 repo 의 **pre-commit 훅이 자동 실행**한다. agent 가 task 본문에서 `./gradlew test` 를 직접 호출하지 않는다. 인-세션 sanity 가 필요하면 `./gradlew compileJava compileTestJava` 만 사용. (참조: `~/.claude/projects/.../memory/feedback_gradle_pre_commit_hook.md`)
7. `PROGRESS.md` 의 해당 row 를 `DONE` + 7자리 커밋 SHA 로 갱신.
8. 본 시리즈의 모든 row 가 `DONE` 이면 `STATUS.md` 를 `ALL DONE`(후행 LF 1개) 으로 교체.
9. 다음 iteration.

## STATUS.md 규약 (엄격)

- `Read` 도구가 종료 판정을 안정적으로 수행할 수 있도록 **파일 전체 내용이 단일 토큰 한 줄 + LF 한 개** 여야 한다.
- 허용값:
  - `IN PROGRESS\n` (작업 중)
  - `ALL DONE\n` (전체 완료)
- markdown 헤더, 주석, 빈 줄, 추가 텍스트 절대 금지.

## PROGRESS.md 규약

- Markdown 테이블 형식.
- 칼럼: `ID | Title | Delta | Status | Depends | Commit`. (greenfield 시리즈의 `Phase` 컬럼이 `Delta` 로 바뀌었다 — D1-D12 의 FRONT_API_SPEC delta ID 기준.)
- `Status`: `TODO` / `IN_PROGRESS` / `DONE` / `BLOCKED`.
- `Depends`: 쉼표 구분 ID 리스트, 의존 없음은 `-`.
- `Commit`: 완료 커밋 SHA 7자리, 미완료는 `-`.
- 헤더 직전에 `Last updated: <ISO-8601 UTC>` 한 줄 유지.

## Task 파일 규약

필수 섹션:
- `## Goal`: 무엇을, 왜 (1~3줄).
- `## Prerequisites`: 선행 task ID 와 제목. 없으면 "없음".
- `## Acceptance Criteria`: 객관적으로 그린/레드 판정 가능한 체크박스 리스트.

선택 섹션:
- `## TDD Entry Points`: RED 단계에서 작성할 구체 테스트 클래스/메서드명.
- `## Files`: 신규/수정 파일 경로.
- `## References`: 권위 문서의 인용 위치.
- `## Commit Message Template`: Conventional Commits 형식 예.

## 현재 시리즈 (FRONT_API_SPEC contract 정렬)

| ID | Title | Delta | 요약 |
|----|-------|-------|------|
| 100 | envelope-and-error-shape | D4 | `ApiResponse` envelope 을 `{ success, data, error }` 중첩 구조로. 모든 controller 테스트 의 jsonPath sweep |
| 101 | auth-dto-alignment | D5+D6+D7 | TokenResponse / LoginRequest / Check{Username,Email} 필드명 정렬 |
| 102 | tts-public-and-lang | D1+D8 | `/api/tts` 공개화 + `locale`→`lang` rename |
| 103 | recording-remove-free-form | D3 | `SESSION_FREE_FORM` 모드 제거 (2-mode 계약). D2 (form-data field) 도 테스트 그린이 자연스럽게 검증 |
| 104 | recording-wrong-words | D10 | `RecordingResult.wrongWords` 를 `WrongWord[]` 로 |
| 105 | feedback-response-shapes | D9 | `retry-word` → `RetryWordResult`, `complete` → `User` |
| 106 | session-list-response | D11 | `GET /api/sessions` 가 전체 `Session[]` (포함 scriptText + sentences) 반환 |
| 107 | script-track-dto-fields | D12 | `preset`→`isPreset` + `chapterCount` / `displayOrder` 필드 정렬 |

## 의존성 그래프

```
100 (envelope foundation — blocks all)
 ├── 101 (auth DTOs)
 │    └── 105 (feedback responses — depends on 101 for User shape)
 ├── 102 (tts public + lang)
 ├── 103 (recording free-form removal)
 ├── 104 (recording wrongWords)
 ├── 106 (session list)
 └── 107 (script/track DTO fields)
```

100 만 hard blocker. 101-107 은 100 이후 병렬 가능 (단, 105 는 101 도 의존).

## 참조

- `docs/IMPLEMENTATION_PLAN.md` — 본 시리즈의 source plan (11 deltas, D1-D12)
- `docs/legacy/IMPLEMENTATION_PLAN.md` — 그린필드 Phase 0~6 archive (2026-05-13)
- `docs/legacy/ralph/` — 완료된 34개 task archive (2026-05-13)
- `docs/API_SPEC_REFINED.md`, `docs/ENTITIES_REFINED.md`, `docs/COMPONENTS_REFINED.md`, `docs/MODEL_SERVER_API_SPEC.md`, `docs/API_TEST_PLAN.md`, `docs/FRONT_API_SPEC.md` — 권위 입력 문서
- `agent/COMMIT_CONVENTIONS.md` — 커밋 메시지 규약
- `CLAUDE.md` — 도메인 5분할, 빌드 명령, 패키지 규약

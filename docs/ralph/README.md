# Ralph Loop — ECHO Backend Task Tracking

`docs/IMPLEMENTATION_PLAN.md` Phase 0~6 을 Ralph Loop 가 iteration 단위로 처리할 수 있도록 34개 독립 task 로 분할한 작업 폴더다.

## 폴더 구성

| 파일 | 역할 |
|---|---|
| `STATUS.md` | 종료 신호 전용. 단일 토큰 `IN PROGRESS` 또는 `ALL DONE`. 후행 LF 1개. 그 외 문자 금지. |
| `PROGRESS.md` | 진행 추적 테이블. 34개 row + Last updated 타임스탬프. |
| `NNN.<kebab-title>.md` | 개별 task 명세. 000~033. |

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
7. `PROGRESS.md` 의 해당 row 를 `DONE` + 7자리 커밋 SHA 로 갱신.
8. 본 task 가 ID=033 이라면 `STATUS.md` 를 `ALL DONE`(후행 LF 1개) 으로 교체.
9. 다음 iteration.

## STATUS.md 규약 (엄격)

- `Read` 도구가 종료 판정을 안정적으로 수행할 수 있도록 **파일 전체 내용이 단일 토큰 한 줄 + LF 한 개** 여야 한다.
- 허용값:
  - `IN PROGRESS\n` (작업 중)
  - `ALL DONE\n` (전체 완료)
- markdown 헤더, 주석, 빈 줄, 추가 텍스트 절대 금지.

## PROGRESS.md 규약

- Markdown 테이블 형식.
- 칼럼: `ID | Title | Phase | Status | Depends | Commit`.
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
- `## Commit Message Template`: Conventional Commits 형식 예.

## 참조

- `docs/IMPLEMENTATION_PLAN.md` §6 (Phase 카드), §7.2 (서비스 도메인 불변식 #1~#15), §3 (도메인 매트릭스)
- `docs/API_SPEC_REFINED.md`, `docs/ENTITIES_REFINED.md`, `docs/COMPONENTS_REFINED.md`, `docs/MODEL_SERVER_API_SPEC.md`, `docs/API_TEST_PLAN.md` — 권위 입력 문서
- `agent/COMMIT_CONVENTIONS.md` — 커밋 메시지 규약
- `CLAUDE.md` — 도메인 5분할, 빌드 명령, 패키지 규약

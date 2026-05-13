# Ralph Loop — One Iteration

너는 ECHO 백엔드(Spring Boot 4.0.5 / Java 25 / JPA / MySQL / H2 테스트 / Spring REST Docs / JaCoCo 80%+) Ralph Loop 의 **한 iteration** 을 실행한다. 본 prompt 호출 한 번에 정확히 **task 1개** 를 닫고 종료한다. 절차의 권위 출처는 `docs/ralph/README.md` 이며 본 prompt 는 그 핵심을 inline 으로 다시 명시한다.

## 0. Pre-flight

1. `Bash: git status --porcelain` — 출력이 있으면 (= 미커밋 변경) 작업을 시작하지 말 것. 사용자가 정리하도록 stderr 에 한 줄 보고 후 즉시 종료.
2. `Bash: git rev-parse --abbrev-ref HEAD` — 결과를 `BASE` 로 캡처. **`BASE` 가 `main` / `master` / `develop` 이면 즉시 종료** + stderr 보고: "ralph-loop 은 보호 브랜치에서 직접 실행할 수 없습니다. 작업용 branch (예: `git switch -c ralph/run-1`) 에서 다시 실행하세요." (이렇게 해야 PROGRESS.md DONE 갱신이 보호 branch 로 누수되지 않고, ralph-loop 진행 commit 들이 PR review 없이 main 에 쌓이는 사고를 막음.)
3. `Read docs/ralph/STATUS.md` — 내용 trim 결과가 정확히 `ALL DONE` 이면 즉시 종료 (할 일 없음). 이 외에는 다음 단계로.

## 1. 다음 task 선정

`Read docs/ralph/PROGRESS.md`. 다음 조건을 모두 만족하는 가장 작은 ID 의 row 1개를 고른다:

- `Status` 가 `TODO` (또는 이전 iteration 이 죽어 남긴 `IN_PROGRESS`).
- `Depends` 칼럼의 모든 ID 가 `Status=DONE`.

후보가 없으면 ("blocked DAG"): stderr 에 "no eligible task" 보고 후 종료.

선정한 ID 를 `<NNN>` 으로 부른다. `Glob docs/ralph/<NNN>.*.md` 로 task 파일 경로 확정 후 `Read` 한다.

## 2. 구현

`Read docs/ralph/<NNN>.*.md` 의 다음 섹션을 모두 충족시킨다:

- `## Goal` — 무엇을, 왜.
- `## Prerequisites` — 선행 task ID. 본 단계에서는 이미 모두 DONE 임이 보장.
- `## Acceptance Criteria` — 체크박스 전부 객관적으로 통과해야 함.
- (선택) `## TDD Entry Points`, `## Files`, `## Commit Message Template`.

권위 입력 문서 (필요할 때만 부분 Read):

- `docs/IMPLEMENTATION_PLAN.md` — 현재 진행 중인 FRONT_API_SPEC contract 정렬 plan (rev6 D1-D3 + Codex 2026-05-13 D4-D12). 그린필드 Phase 0~6 의 §6 / §7.2 / §3 archive 는 `docs/legacy/IMPLEMENTATION_PLAN.md` 참조.
- `docs/API_SPEC_REFINED.md`, `docs/ENTITIES_REFINED.md`, `docs/COMPONENTS_REFINED.md`, `docs/MODEL_SERVER_API_SPEC.md`, `docs/API_TEST_PLAN.md`.
- `CLAUDE.md` — 패키지/도메인/빌드 규약.

작성 순서는 **springboot-tdd** 스킬: RED (실패 테스트 먼저) → GREEN (최소 구현) → REFACTOR (정리). 컨트롤러는 MockMvc + Spring REST Docs (`@AutoConfigureRestDocs`), 서비스 불변식은 `@SpringBootTest` + `@Transactional`. 외부 어댑터는 `@MockitoBean` (Spring Framework 6.2 `org.springframework.test.context.bean.override.mockito.MockitoBean`) 로 격리.

## 3. 브랜치 전략 (너의 판단)

루프가 시작될 때 체크아웃되어 있던 branch 를 `BASE` 라 부른다 (`git rev-parse --abbrev-ref HEAD` 로 캡처). task 의 성격에 따라 다음 중 하나를 선택:

- **소규모 / 설정성 task** (예: yaml 추가, 단일 인터페이스 도입): `BASE` 에 직접 commit. 별도 branch 생성 안 함.
- **응집된 큰 task 또는 IMPLEMENTATION_PLAN §6 의 "산출 PR" 경계에 해당**: `git switch -c ralph/<NNN>-<짧은-제목>` 으로 신규 branch 생성, 거기서 작업.

판단이 모호하면 default 는 `BASE` 직접 commit.

## 4. 커밋

`agent/COMMIT_CONVENTIONS.md` 규약(`<type>(<scope>): <subject>`)을 따른다. task 파일의 `## Commit Message Template` 이 있으면 그것을 시작점으로 한다. 한 task 가 여러 단계라면 단계별로 여러 commit 가능 (RED commit / GREEN commit / REFACTOR commit).

**중요: PreToolUse 훅** (`.claude/hooks/test.sh`) 이 모든 `git commit` 직전에 `./gradlew test` 를 자동 실행한다.

- 훅 exit 0 → commit 진행.
- 훅 exit 2 → 테스트/컴파일 실패. stderr 를 읽고 **수정 후 같은 commit 재시도**. 절대 `--no-verify`, `SKIP_PRECOMMIT_TESTS=1`, 또는 다른 우회 수단 사용 금지.
- 훅 exit 1 → 인프라 문제. stderr 에 보고 후 즉시 종료. 자동 수정 시도하지 말 것.

## 5. Acceptance Criteria 검증

task 파일의 모든 `- [ ]` 항목이 객관적으로 통과해야 한다. 미통과 항목이 있으면 §2~§4 재진입. JaCoCo 80%+ 가 항목에 포함된 경우 `./gradlew jacocoTestCoverageVerification` 로 실측.

## 6. 상태 갱신 (최종 commit)

`docs/ralph/PROGRESS.md` / `docs/ralph/STATUS.md` 는 **`BASE` branch 에서 읽힌다**. 따라서 갱신 commit 은 다음과 같이:

- §3 에서 `BASE` 에 직접 commit 한 경우: PROGRESS/STATUS 갱신도 그대로 `BASE` 에 commit.
- §3 에서 `ralph/<NNN>-...` 신규 branch 를 만든 경우:
  1. 그 branch 의 마지막 commit 으로 PROGRESS/STATUS 갱신 포함.
  2. `git switch <BASE>` 로 복귀.
  3. `git merge --no-ff ralph/<NNN>-<...>` 로 task branch 를 BASE 에 머지 (이게 다음 iteration 이 DONE 상태를 보는 핵심).

갱신 내용:
- PROGRESS.md 해당 row: `Status=DONE`, `Commit=<구현 최종 commit SHA 7자리>`. `Last updated:` 헤더의 ISO-8601 UTC 갱신.
- ID 가 본 시리즈의 마지막 ID (현재 `docs/ralph/PROGRESS.md` 의 최대 ID — rev6+Codex 시리즈에서는 `107`) 인 경우에만: `docs/ralph/STATUS.md` 파일 내용을 정확히 `ALL DONE\n` (단일 라인 + LF 1개) 로 교체.
- 갱신 commit 메시지 예: `chore(ralph): mark <NNN> DONE`.

## 7. Push & PR (자동화)

`git remote -v` 결과에 `origin` 이 있고 네트워크 가용한 경우에만:

**main / master / develop 으로의 직접 push 는 절대 금지.** §0 에서 BASE 가 이미 비보호 branch 임이 보장되었지만, PR 의 target 이 main 일 수 있으므로 push 대상은 다음 규칙:

- `BASE` 직접 commit 한 경우: `git push origin <BASE>` (BASE 가 비보호이므로 안전). PR 은 BASE 의 누적 진행을 위해 별도 단계에서 사용자 책임으로 생성 — 본 prompt 에서 자동 PR 만들지 않음.
- 신규 task branch 를 만들고 §6 에서 BASE 로 머지한 경우:
  1. `git push -u origin ralph/<NNN>-<...>` (task branch 푸시).
  2. `gh pr create --base <BASE> --head ralph/<NNN>-<...> --title "<type>(<scope>): NNN <title>" --body "..."` 로 PR 생성. body 는 task 의 Goal + Acceptance Criteria 요약 + 본 PR 의 commit 목록 + "Ralph Loop iteration 의 일부" 라는 컨텍스트.
  3. `git push origin <BASE>` (BASE 도 origin 으로 푸시. BASE 는 비보호 branch).

PR 생성·푸시 실패는 치명적이지 않음. stderr 에 경고하고 계속 진행. 작업 자체는 로컬에서 이미 완료된 상태.

> 참고: ALL DONE 도달 후 BASE → main 으로의 최종 통합 PR 은 사용자가 직접 생성·머지한다. 본 prompt 가 main 으로 PR 을 만들 수는 있지만, **main 직접 push 는 어떠한 경우에도 하지 않는다**.

## 8. 출구

`git switch <BASE>` 가 보장된 상태(체크아웃 위치 = BASE)에서 본 iteration 종료. 다음 iteration 은 `ralph-loop.sh` 가 처리한다. 추가 task 를 시도하지 말 것.

## Hard Constraints

- **One task per invocation**. 끝나면 그만.
- `--no-verify` / `SKIP_PRECOMMIT_TESTS=1` / 훅 우회 일체 금지.
- **iteration 종료 시점에 반드시 `BASE` branch 에 체크아웃되어 있을 것** (`git rev-parse --abbrev-ref HEAD == BASE`). 다음 iteration 이 의존.
- **`BASE` branch 에 PROGRESS.md DONE 갱신이 반드시 반영**되어 있을 것 (직접 commit 또는 머지). 안 그러면 다음 iteration 이 같은 task 를 다시 픽업.
- **main / master / develop 으로의 직접 push 절대 금지.** `git push origin main` 같은 명령은 어떤 상황에서도 실행하지 않음. main 통합은 PR + 사용자 머지로만.
- `git push --force`, `git reset --hard`, `git branch -D` 등 파괴적 명령 금지. PR 머지·closure 는 사용자 결정 영역.
- 도구 사용 권한은 auto 모드이지만, `rm -rf`, `git clean -fd` 등 파일 삭제는 task 가 명시적으로 요구할 때만.
- 의심스러우면 BLOCKED: PROGRESS.md row 의 `Commit` 칼럼에 `BLOCKED:<사유>` 기록 + Status 는 `BLOCKED` 로 갱신, BASE 에 commit 후 종료. 다음 iteration 까지 사용자가 결정.

이제 §0 부터 시작한다.

#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# gradlew 테스트 실행: 테스트 실패, 컴파일 실패 -> exit code 2 / gradle 자체 문제, 환경 문제 -> exit code 1

# Escape hatch (외부에서 SKIP_PRECOMMIT_TESTS=1 로 설정 시 우회). 기본값은 0(=실행).
: "${SKIP_PRECOMMIT_TESTS:=0}"

# ---- 1. stdin JSON 파싱 ---------------------------------------------------

INPUT="$(cat || true)"

detect_python() {
  # python3, python, py 중 동작하는 실 인터프리터 1개 탐색.
  # Windows 의 Microsoft Store 스텁(WindowsApps/python3.exe)은 호출 시 Store 를 띄우므로 제외.
  local cand cand_path
  for cand in python3 python py; do
    if cand_path=$(command -v "$cand" 2>/dev/null) && [[ -n "$cand_path" ]]; then
      if [[ "$cand_path" == *"WindowsApps"* ]]; then
        continue
      fi
      if "$cand" -c 'import sys, json' >/dev/null 2>&1; then
        echo "$cand"
        return 0
      fi
    fi
  done
  return 1
}

extract_json_field() {
  # $1 = jq path (e.g. .tool_name, .tool_input.command)
  local path="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$INPUT" | jq -r "$path // empty" 2>/dev/null || true
    return
  fi
  local py
  if py="$(detect_python)"; then
    # JSON 과 path 를 argv 로 전달 (heredoc + stdin 충돌 회피).
    PYTHONPATH= "$py" -c '
import sys, json
try:
    data = json.loads(sys.argv[1])
    keys = sys.argv[2].lstrip(".").split(".")
    for key in keys:
        if isinstance(data, dict):
            data = data.get(key, "")
        else:
            data = ""
            break
    print(data if isinstance(data, str) else "")
except Exception:
    pass
' "$INPUT" "$path" 2>/dev/null || true
    return
  fi
  echo "[test.sh] jq 또는 python(3) 중 하나가 필요합니다." >&2
  exit 1
}

TOOL_NAME="$(extract_json_field .tool_name)"
COMMAND="$(extract_json_field .tool_input.command)"

# ---- 2. 스킵 판정 ---------------------------------------------------------

if [[ "$SKIP_PRECOMMIT_TESTS" == "1" ]]; then
  echo "[test.sh] SKIP_PRECOMMIT_TESTS=1 — gradlew test 우회" >&2
  exit 0
fi

if [[ "$TOOL_NAME" != "Bash" ]]; then
  exit 0
fi

# ---- 3. git commit 패턴 매칭 ---------------------------------------------

is_git_commit() {
  local cmd="$1"
  # &&, ||, ;, | 로 분할 후 세그먼트별 검사 (IFS 한 글자씩 split)
  local seg
  local IFS='&|;'
  for seg in $cmd; do
    # "git [옵션...] commit (단어경계)" 매칭
    # 허용: `git commit`, `git -C path commit`, `git -c k=v commit`, `git --no-pager commit`
    # 거부: `git commit-tree`, `git log`, `git status`
    if printf '%s' "$seg" | grep -qE '(^|[[:space:]])git([[:space:]]+(-[Cc][[:space:]]+[^[:space:]]+|-[^[:space:]]+))*[[:space:]]+commit([[:space:]]|$)'; then
      return 0
    fi
  done
  return 1
}

if ! is_git_commit "$COMMAND"; then
  exit 0
fi

# ---- 4. gradlew test 실행 ------------------------------------------------

cd "$PROJECT_ROOT"

if [[ ! -x ./gradlew ]]; then
  echo "[test.sh] ./gradlew 가 실행 불가: $PROJECT_ROOT/gradlew" >&2
  exit 1
fi

echo "[test.sh] git commit 감지 — ./gradlew test 실행 (PROJECT_ROOT=$PROJECT_ROOT)" >&2

TMP_OUT="$(mktemp)"
trap 'rm -f "$TMP_OUT"' EXIT

set +e
./gradlew test --console=plain 2>&1 | tee "$TMP_OUT" >&2
GRADLE_EXIT=${PIPESTATUS[0]}
set -e

if [[ $GRADLE_EXIT -eq 0 ]]; then
  echo "[test.sh] gradlew test 통과 — commit 진행" >&2
  exit 0
fi

# ---- 5. 실패 분류 ---------------------------------------------------------

if grep -qE '(> Task :[A-Za-z0-9_:-]*[Tt]est[A-Za-z0-9_:-]* FAILED|> Task :compile[A-Za-z]+ FAILED|Compilation failed|There were failing tests|FAILED$)' "$TMP_OUT"; then
  echo "[test.sh] 테스트/컴파일 실패 감지 — exit 2 (Claude 자가수정 필요)" >&2
  exit 2
fi

echo "[test.sh] gradle 실행 환경/인프라 문제로 보임 — exit 1 (사용자 확인 필요)" >&2
exit 1

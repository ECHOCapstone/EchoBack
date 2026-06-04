#!/usr/bin/env bash
# ARPAbet 음소 이미지 39장을 한 번에 백엔드에 등록한다.
# - 로컬 로그인으로 admin JWT 를 받아 Authorization 헤더에 싣는다.
# - 디렉터리 안의 *.png 를 파일명 = phoneme 으로 매핑해 POST /api/admin/phonemes/{phoneme}/image 호출.
# - 백엔드가 디스크 저장 (app.storage.local-root/phonemes/<phoneme>.png) + phoneme_assets DB 업서트를 함께 처리한다.
#
# 사용:
#   BASE_URL=http://localhost:8080 \
#   ADMIN_USERNAME=admin \
#   ADMIN_PASSWORD='EchoAdmin2026!' \
#   IMAGE_DIR=/tmp/phonemes \
#   ./scripts/upload-phonemes.sh

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USERNAME="${ADMIN_USERNAME:?ADMIN_USERNAME 가 필요합니다 (예: admin)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?ADMIN_PASSWORD 가 필요합니다}"
IMAGE_DIR="${IMAGE_DIR:?IMAGE_DIR 가 필요합니다 (zip 을 푼 디렉터리)}"

if [[ ! -d "$IMAGE_DIR" ]]; then
  echo "ERROR: IMAGE_DIR=$IMAGE_DIR 가 존재하지 않습니다." >&2
  exit 1
fi

echo ">> 로그인: ${BASE_URL} (username=${ADMIN_USERNAME})"
LOGIN_BODY=$(jq -nc --arg u "$ADMIN_USERNAME" --arg p "$ADMIN_PASSWORD" '{username:$u, password:$p}')
LOGIN_RESPONSE=$(curl -sS -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$LOGIN_BODY")

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.token // .data.accessToken // .token // empty')
if [[ -z "$TOKEN" ]]; then
  echo "ERROR: 로그인 응답에서 토큰을 찾지 못했습니다." >&2
  echo "응답: $LOGIN_RESPONSE" >&2
  exit 1
fi
echo "   토큰 획득 (${#TOKEN} bytes)"

OK=0
FAIL=0
shopt -s nullglob
for img in "$IMAGE_DIR"/*.png; do
  name=$(basename "$img" .png)
  printf ">> %-3s  " "$name"
  HTTP_CODE=$(curl -sS -o /tmp/phoneme-upload.json -w '%{http_code}' \
    -X POST "${BASE_URL}/api/admin/phonemes/${name}/image" \
    -H "Authorization: Bearer ${TOKEN}" \
    -F "image=@${img};type=image/png")
  if [[ "$HTTP_CODE" == "200" ]]; then
    echo "OK"
    OK=$((OK + 1))
  else
    echo "FAIL (HTTP $HTTP_CODE)"
    cat /tmp/phoneme-upload.json
    echo
    FAIL=$((FAIL + 1))
  fi
done

echo
echo "완료: 성공 $OK, 실패 $FAIL"
[[ $FAIL -eq 0 ]]

#!/usr/bin/env bash
# 서버에서 도는 복구 스크립트 — 컨테이너를 다시 세운다(#297).
#
# **왜 파일로 뺐나.** 예전에는 이 로직이 "기동 확인 · 스모크" 스텝의 heredoc 안에 함수로 있었다.
# 그런데 그 앞 스텝(컨테이너 교체)이 기존 컨테이너를 지운 뒤 실패하면, 스모크 스텝은 기본 조건
# (`if: success()`)때문에 통째로 건너뛰어진다 — **함수가 정의된 곳이 안 돌아 롤백도 안 돈다.**
# 롤백 장치가 있는데 정작 서비스가 죽는 경로에서만 없었다.
#
# 이제 두 스텝이 이 한 파일을 부른다. 복구 방식이 갈릴 자리가 없다.
#
# **종료코드는 언제나 1 이다.** 복구에 성공해도 그 배포는 실패한 것이다 — 되돌아갔든 유지했든
# 이번에 올리려던 버전이 서비스되고 있지 않다. "서비스가 살아 있는가" 는 종료코드가 아니라
# 아래 표식으로 알린다(배포 알림이 이 문장들을 읽는다).
#
#   롤백 완료                    → 이전 버전으로 서비스 중
#   대체 기동 확인               → 롤백 대상이 없어 방금 배포한 것으로 유지
#   롤백까지 실패                → 서비스 중단
#   대체 기동까지 실패           → 서비스 중단
#   되돌릴 이전 이미지가 없습니다 → 서비스 중단(첫 배포)
#
# 문구를 고치면 `.github/workflows/deploy.yml` 의 알림 분기도 함께 고쳐야 한다.
set -uo pipefail

APP=~/offway
CONTAINER=offway-core
# 401 도 정상이다 — 인증 게이트가 살아 있다는 뜻이다(#122).
HEALTH_URL=http://localhost:8080/api/v1/categories
TRIES=30
INTERVAL=5

cd "$APP" || exit 1

echo "── 배포 실패. 최근 로그 ──"
docker logs --tail 120 "$CONTAINER" 2>&1 || true

# 컨테이너를 띄우고 실제 응답을 기다린다.
#
# `docker run -d` 는 **컨테이너 생성까지만** 본다. 곧바로 죽는 경우(DB 접속 실패·마이그레이션 실패)가
# 실제로 있어서, 떴다는 사실만으로 복구를 성공이라 부르면 서비스가 없는 채로 초록불이 뜬다.
start_and_wait() {
  image="$1"
  docker rm -f "$CONTAINER" 2>/dev/null || true
  # **stdout 을 로그 쪽으로 돌린다.** 이 함수는 응답 코드를 stdout 으로 돌려주는데, `docker run -d` 도
  # 컨테이너 ID 를 stdout 에 찍는다. 그대로 두면 호출부의 `code=$(start_and_wait ...)` 가 둘을 함께
  # 받아 "HTTP 3f9a…401" 같은 문구가 나간다. stderr 도 로그로 합쳐지므로 ID 가 사라지지는 않는다.
  docker run -d --name "$CONTAINER" --network offway-net --restart unless-stopped \
    --env-file "$APP/env.prod" -p 8080:8080 "$image" >&2
  for _ in $(seq 1 "$TRIES"); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)
    if [ "$code" = "401" ] || [ "$code" = "200" ]; then
      echo "$code"
      return 0
    fi
    sleep "$INTERVAL"
  done
  return 1
}

PREV=$(cat "$APP/.prev-image" 2>/dev/null || echo "")

if [ -z "$PREV" ]; then
  echo "되돌릴 이전 이미지가 없습니다(첫 배포). 컨테이너를 내린 채 둡니다."
  exit 1
fi

if ! docker image inspect "$PREV" >/dev/null 2>&1; then
  # 되돌아갈 이미지가 사라졌다. 죽은 채 두는 것이 가장 나쁘므로, 방금 배포한 것으로라도 서비스를 세운다 —
  # 기동은 됐는데 스모크만 실패한 경우가 대부분이라 대개 이쪽이 낫다.
  echo "!! 롤백 이미지($PREV)가 없습니다. 방금 배포한 버전으로 서비스를 유지합니다 !!"
  if code=$(start_and_wait "$CONTAINER:latest"); then
    echo "!! 대체 기동 확인 (HTTP $code). 배포는 실패로 처리되지만 서버는 떠 있습니다 !!"
    exit 1
  fi
  echo "!! 대체 기동까지 실패했습니다. 수동 개입이 필요합니다 !!"
  exit 1
fi

echo "── $PREV 로 롤백합니다 ──"
if code=$(start_and_wait "$PREV"); then
  echo "롤백 완료 — 이전 버전으로 서비스 중 (HTTP $code)"
  exit 1
fi
echo "롤백까지 실패했습니다. 수동 개입이 필요합니다."
exit 1

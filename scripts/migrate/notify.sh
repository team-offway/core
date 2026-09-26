#!/usr/bin/env bash
# 이관 진행을 디스코드로 알린다(#576).
#
# ## 왜 스크립트로 빼나
#
# 이사는 20~30분 걸리고 단계가 일곱이다. 알림을 워크플로에 인라인으로 쓰면 같은 전송 코드가
# 단계마다 복사되고, 그 복사본 하나만 어긋나도 그 단계만 조용히 알림이 안 간다.
#
# ## 모드를 모든 줄에 박는다
#
# **리허설과 본 이사는 결과가 전혀 다르다** — 리허설은 트래픽을 안 넘기고 인스턴스를 남긴다.
# 그런데 알림은 한 줄씩 따로 도착하고, 스크롤해서 읽는다. 시작 알림에만 "리허설" 을 적으면
# 중간 줄을 본 사람이 본 이사로 읽는다. 그래서 `MIGRATE_MODE` 를 **모든 메시지 앞에** 붙인다.
# 반복이 곧 목적이다.
#
# ## 전송 실패가 이관 결과를 바꾸지 않는다
#
# 알림이 못 갔다고 이사를 실패로 만들지 않는다 — 그러면 멀쩡한 이사가 웹훅 장애로 중단된다.
# 다만 **못 보냈으면 못 보냈다고 남긴다**(`::warning`). 조용히 안 오는 알림은 없는 알림과 같고,
# 그 사실조차 아무도 모른다(deploy.yml 이 같은 판단을 적어 뒀다).
#
# ## 웹훅 URL 은 출력에 남기지 않는다
#
# 그 자체가 자격증명이다. `curl -s` 로 진행률도 끄고 상태코드만 찍는다.
#
# 사용법:
#   notify.sh start  "<제목>"  "<본문>"
#   notify.sh begin  "<n/N>"   "<단계 이름>"
#   notify.sh sub    "<n/N>"   "<한 줄 — 단계 내부 진행>"
#   notify.sh step   "<n/N>"   "<단계 이름>"  "완료|실패|건너뜀"  "<한 줄 상세>"
#   notify.sh done   "<제목>"  "<본문>"
#   notify.sh fail   "<n/N>"   "<단계 이름>"  "<무엇이 어떻게 됐나>"
set -uo pipefail

KIND=${1:?kind 가 필요합니다}
WEBHOOK=${MIGRATE_WEBHOOK:-}

if [ -z "$WEBHOOK" ]; then
  # 일반 로그로만 남기면 실행 요약에 안 보여, 알림이 한 줄도 안 온 이유를 모른 채 20~30분을 기다린다.
  echo "::warning title=이관 알림 없음::이관 알림 웹훅이 없어 건너뜁니다 (DISCORD_MIGRATE_WEBHOOK_URL·DISCORD_DEPLOY_WEBHOOK_URL 을 확인하세요)"
  exit 0
fi

RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-team-offway/core}/actions/runs/${GITHUB_RUN_ID:-0}"

# **모드를 추측하지 않는다.** 비었으면 "리허설" 로 가정하는 것이 안전해 보이지만, 본 이사에서
# 환경변수를 빠뜨리면 트래픽을 넘기는 회차가 리허설로 보고된다 — 거짓 안심이 가장 나쁘다.
MODE=${MIGRATE_MODE:-}
[ -n "$MODE" ] || MODE='모드 미지정'
BADGE=$(printf '`%s`' "$MODE")

# 경과 시간은 파일에 찍어 스텝 사이로 넘긴다 — `run` 블록마다 셸이 새로 떠서 변수가 안 남는다.
STAMP_DIR=${RUNNER_TEMP:-/tmp}
RUN_STAMP="$STAMP_DIR/migrate-run.epoch"
STAGE_STAMP="$STAMP_DIR/migrate-stage.epoch"

# 단계는 순서대로 도니 파일 하나를 덮어쓰면 된다. 단계별 파일을 두면 `n/N` 의 `/` 를 파일명으로
# 쓸 수 없어 이름을 또 다듬어야 하고, 그 다듬기가 어긋나면 엉뚱한 단계의 시각을 읽는다.
elapsed() {
  [ -s "$1" ] || return 0
  start=$(cat "$1" 2>/dev/null) || return 0
  case "$start" in ''|*[!0-9]*) return 0 ;; esac
  d=$(( $(date +%s) - start ))
  [ "$d" -lt 0 ] && return 0
  if [ "$d" -ge 60 ]; then
    printf '%d분 %d초' $((d / 60)) $((d % 60))
  else
    printf '%d초' "$d"
  fi
}

# 포맷 문자열은 **작은따옴표**다. 백틱은 디스코드의 인라인 코드 표기라 리터럴이어야 하는데,
# 큰따옴표로 감싸면 셸이 명령 치환으로 읽어 안의 내용을 실행하려 든다(deploy.yml 의 그 함정).
# shellcheck disable=SC2016
case "$KIND" in
  start)
    date +%s > "$RUN_STAMP"
    TEXT=$(printf '## 이관 시작 · %s\n%s\n%s\n실행: %s' "$MODE" "${2:-}" "${3:-}" "$RUN_URL")
    ;;
  begin)
    date +%s > "$STAGE_STAMP"
    TEXT=$(printf '%s **[%s] %s** — 시작' "$BADGE" "${2:-}" "${3:-}")
    ;;
  sub)
    # `[2/5]` 를 여기도 붙인다 — 알림은 한 줄씩 도착하니, 줄 하나만 봐도 어느 단계인지 알아야 한다.
    TEXT=$(printf '`%s %s` %s' "$MODE" "${2:-}" "${3:-}")
    ;;
  step)
    TOOK=$(elapsed "$STAGE_STAMP")
    HEAD=$(printf '%s **[%s] %s — %s**' "$BADGE" "${2:-}" "${3:-}" "${4:-}")
    [ -n "$TOOK" ] && HEAD=$(printf '%s · %s' "$HEAD" "$TOOK")
    DETAIL=${5:-}
    if [ -n "$DETAIL" ]; then
      TEXT=$(printf '%s\n%s' "$HEAD" "$DETAIL")
    else
      TEXT=$HEAD
    fi
    ;;
  done)
    TOOK=$(elapsed "$RUN_STAMP")
    HEAD=$(printf '## %s · %s' "${2:-}" "$MODE")
    [ -n "$TOOK" ] && HEAD=$(printf '%s (총 %s)' "$HEAD" "$TOOK")
    TEXT=$(printf '%s\n%s\n실행: %s' "$HEAD" "${3:-}" "$RUN_URL")
    ;;
  fail)
    TEXT=$(printf '## 이관 실패 · %s — `[%s]` %s\n%s\n실행: %s' \
      "$MODE" "${2:-}" "${3:-}" "${4:-}" "$RUN_URL")
    ;;
  *)
    echo "알 수 없는 알림 종류: $KIND" >&2
    exit 1
    ;;
esac

# 본문은 jq 로 만든다 — 상세에 따옴표·개행이 섞여도 JSON 이 깨지지 않는다.
TMP=$(mktemp)
jq -n --arg content "$TEXT" '{content: $content}' > "$TMP"

# **HTTPS 로만 보낸다.** 웹훅 주소는 그 자체가 게시 권한이라, http 로 나가면 경로 위의 누구든 주소를
# 주워 우리 채널에 아무 글이나 올릴 수 있다. 시크릿을 잘못 넣은 경우를 여기서 거른다 — 알림 실패가
# 이관을 멈추지 않는 규칙은 그대로다.
case "$WEBHOOK" in
  https://*) ;;
  *)
    rm -f "$TMP"
    echo "::warning title=이관 알림 실패::웹훅 주소가 https 가 아니어서 보내지 않았습니다"
    exit 0
    ;;
esac

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 --proto '=https' \
  -H 'Content-Type: application/json' \
  -d "@$TMP" "$WEBHOOK" || echo "000")
rm -f "$TMP"

if [ "$code" = "204" ] || [ "$code" = "200" ]; then
  echo "이관 알림 전송 (HTTP $code)"
else
  echo "::warning title=이관 알림 실패::디스코드가 HTTP $code 로 답해 알림이 전달되지 않았습니다 (웹훅 URL·권한을 확인하세요)"
fi

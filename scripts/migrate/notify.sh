#!/usr/bin/env bash
# 이관 진행을 디스코드로 알린다(#576).
#
# ## 왜 스크립트로 빼나
#
# 이사는 20~30분 걸리고 단계가 일곱이다. 알림을 워크플로에 인라인으로 쓰면 같은 전송 코드가
# 단계마다 복사되고, 그 복사본 하나만 어긋나도 그 단계만 조용히 알림이 안 간다.
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
#   notify.sh start   "<제목>"  "<본문>"
#   notify.sh step    "<n/N>"   "<단계 이름>"  "완료|실패|건너뜀"  "<한 줄 상세>"
#   notify.sh done    "<제목>"  "<본문>"
#   notify.sh fail    "<n/N>"   "<단계 이름>"  "<무엇이 어떻게 됐나>"
set -uo pipefail

KIND=${1:?kind 가 필요합니다}
WEBHOOK=${MIGRATE_WEBHOOK:-}

if [ -z "$WEBHOOK" ]; then
  echo "이관 알림 웹훅이 없어 건너뜁니다 (DISCORD_DEPLOY_WEBHOOK_URL 을 확인하세요)"
  exit 0
fi

RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-team-offway/core}/actions/runs/${GITHUB_RUN_ID:-0}"

# 포맷 문자열은 **작은따옴표**다. 백틱은 디스코드의 인라인 코드 표기라 리터럴이어야 하는데,
# 큰따옴표로 감싸면 셸이 명령 치환으로 읽어 안의 내용을 실행하려 든다(deploy.yml 의 그 함정).
# shellcheck disable=SC2016
case "$KIND" in
  start)
    TEXT=$(printf '## 이관 시작 — %s\n%s\n실행: %s' "${2:-}" "${3:-}" "$RUN_URL")
    ;;
  step)
    # [3/5] 데이터 이관 — 완료 · 51개 표 402,518행 일치
    DETAIL=${5:-}
    if [ -n "$DETAIL" ]; then
      TEXT=$(printf '`[%s]` **%s** — %s\n%s' "${2:-}" "${3:-}" "${4:-}" "$DETAIL")
    else
      TEXT=$(printf '`[%s]` **%s** — %s' "${2:-}" "${3:-}" "${4:-}")
    fi
    ;;
  done)
    TEXT=$(printf '## %s\n%s\n실행: %s' "${2:-}" "${3:-}" "$RUN_URL")
    ;;
  fail)
    TEXT=$(printf '## 이관 실패 — `[%s]` %s\n%s\n실행: %s' "${2:-}" "${3:-}" "${4:-}" "$RUN_URL")
    ;;
  *)
    echo "알 수 없는 알림 종류: $KIND" >&2
    exit 1
    ;;
esac

# 본문은 jq 로 만든다 — 상세에 따옴표·개행이 섞여도 JSON 이 깨지지 않는다.
TMP=$(mktemp)
jq -n --arg content "$TEXT" '{content: $content}' > "$TMP"

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
  -H 'Content-Type: application/json' \
  -d "@$TMP" "$WEBHOOK" || echo "000")
rm -f "$TMP"

if [ "$code" = "204" ] || [ "$code" = "200" ]; then
  echo "이관 알림 전송 (HTTP $code)"
else
  echo "::warning title=이관 알림 실패::디스코드가 HTTP $code 로 답해 알림이 전달되지 않았습니다 (웹훅 URL·권한을 확인하세요)"
fi

#!/usr/bin/env bash
# 실측·조사가 쓴 외부 API 콜을 집계에 남기고 디스코드로 알린다.
#
# ## 왜 필요한가
#
# **조사·실측도 한도를 쓴다.** 그런데 그 소비는 앱을 거치지 않아 `ExternalApiCallRecorder` 가 모른다.
# 그러면 두 가지가 동시에 틀어진다.
#
#   ① 앱은 한도가 남았다고 판단한다 — 실제로는 우리가 이미 썼는데 배치가 그걸 모르고 발사한다
#   ② 한도 알림(#257·#285)이 안 뜬다 — 10% 단계마다 울리는 그 알림은 집계를 보고 판단한다
#
# 2026-08-11 에 응답 필드 채움률을 재느라 500건 넘게 쓰고 그날 운영 코스 생성을 degrade 시킨 적이
# 있다. 그때 조용했던 이유가 정확히 이것이다 — **아무 기록도 안 남는 소비**였다.
#
# 이 작업(#590)에서도 열차역 간선 여부를 재느라 1,000콜 넘게 썼고, 그 역시 집계에 없었다.
# 같은 일이 또 있을 것이므로 도구로 만들어 둔다.
#
# ## 쓰는 법 — 실측 스크립트가 앞뒤로 한 번씩 부른다
#
#     scripts/external/probe-notify.sh start  TRAIN_INFO train-intercity 1400
#     ... 실측 ...
#     scripts/external/probe-notify.sh finish TRAIN_INFO train-intercity 1073
#
# `start` 는 **재기 전에 남은 한도를 알린다** — 그 숫자를 보고 멈출 수 있어야 의미가 있다.
# `finish` 는 실제로 쓴 값을 집계에 더하고 누적을 알린다.
#
# ## 운영 서버에서 돈다
#
# 키와 웹훅이 `~/offway/env.prod` 에 있고 집계 DB 가 같은 호스트의 컨테이너에 있다. 실측을 로컬에서
# 돌리면 시크릿을 내려받아야 하므로, 서버에서 돌리는 쪽이 노출이 적다.
set -uo pipefail

ACTION=${1:?start|finish 가 필요합니다}
API=${2:?ExternalApi enum 이름이 필요합니다 (예: TRAIN_INFO)}
PROBE=${3:?실측 이름이 필요합니다 (예: train-intercity)}
COUNT=${4:?콜 수가 필요합니다}

ENV_FILE=${OFFWAY_ENV_FILE:-$HOME/offway/env.prod}
CONTAINER=${OFFWAY_DB_CONTAINER:-offway-mysql}
SCHEMA=${OFFWAY_DB_SCHEMA:-offway}

# **주체 이름에 접두를 붙인다.** `Caller` 는 배치 이름과 `METHOD /path` 패턴을 구분해 사용자 요청
# 비중을 낸다(#398). 실측은 둘 다 아니므로 `probe:` 로 한 버킷에 모아, 심사 자료에서 사람이 돌린
# 조사가 배치로 섞이지 않게 한다.
CALLER="probe:$PROBE"

if [ ! -r "$ENV_FILE" ]; then
  echo "환경 파일을 못 읽습니다: $ENV_FILE" >&2
  exit 1
fi

# 값은 읽어 쓰기만 하고 출력하지 않는다.
WEBHOOK=$(grep '^DISCORD_WEBHOOK_URL=' "$ENV_FILE" | cut -d= -f2-)

mysql_in() {
  # 비밀번호를 argv 에 싣지 않는다 — `ps` 로 보인다. 컨테이너 환경변수를 그대로 쓴다.
  docker exec -i "$CONTAINER" sh -c \
    "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot --default-character-set=utf8mb4 -N -B $SCHEMA"
}

limit_of() {
  # 한도는 `ExternalApi` enum 이 정본이다. 셸에서 그걸 읽을 방법이 없어 여기 적는데, **틀리면
  # 조용히 잘못된 퍼센트를 알리므로** 모르는 API 는 0 으로 두고 퍼센트를 생략한다.
  case "$1" in
    TOUR_API|TOUR_VISITOR|TOUR_HUB_ATTRACTION|TOUR_RELATED_ATTRACTION|TOUR_GALLERY) echo 1000 ;;
    TMAP_WAYPOINT) echo 50 ;;
    TMAP_ROUTE|GO_CAMPING|PET_TOUR|TATS_CROWD_RATE) echo 1000 ;;
    HOLIDAY|BUS_STOP|BUS_ARRIVAL|TRAIN_INFO|EXPRESS_BUS_INFO|INTERCITY_BUS_INFO|SHIP_INFO|KMA_WEATHER|FESTIVAL_STANDARD) echo 10000 ;;
    KAKAO_LOCAL) echo 100000 ;;
    *) echo 0 ;;
  esac
}

used_today() {
  printf "SELECT COALESCE(call_count, 0) FROM external_api_call WHERE call_date = CURDATE() AND api = '%s'" "$API" \
    | mysql_in 2>/dev/null | head -1
}

notify() {
  [ -n "$WEBHOOK" ] || { echo "웹훅이 없어 알림을 건너뜁니다"; return 0; }
  tmp=$(mktemp)
  jq -n --arg content "$1" '{content: $content}' > "$tmp"
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
    -H 'Content-Type: application/json' -d "@$tmp" "$WEBHOOK" || echo "000")
  rm -f "$tmp"
  # 알림이 못 갔다고 실측을 멈추지 않는다. 다만 못 갔다는 사실은 남긴다.
  case "$code" in
    200|204) echo "알림 전송 (HTTP $code)" ;;
    *) echo "알림 실패 (HTTP $code) — 웹훅을 확인하세요" >&2 ;;
  esac
}

LIMIT=$(limit_of "$API")
USED=$(used_today)
USED=${USED:-0}

case "$ACTION" in
  start)
    LINE="오늘 누적 ${USED}"
    [ "$LIMIT" -gt 0 ] && LINE="$LINE/${LIMIT} · 이 실측 뒤 예상 $((USED + COUNT))/${LIMIT}"
    notify "$(printf '## 외부 API 실측 시작 — %s\n`%s` 가 약 %s콜을 씁니다.\n%s\n\n**사용자 요청이 아니라 우리 조사입니다.** 한도가 빡빡하면 지금 멈추세요.' \
      "$API" "$CALLER" "$COUNT" "$LINE")"
    ;;
  finish)
    # **집계에 더한다.** 이것이 이 스크립트의 핵심이다 — 알림만 보내고 집계를 비우면 앱이 계속
    # 한도를 잘못 안다. 두 표(총계·주체별)를 함께 올린다.
    {
      printf "INSERT INTO external_api_call (call_date, api, call_count) VALUES (CURDATE(), '%s', %s) " "$API" "$COUNT"
      printf "ON DUPLICATE KEY UPDATE call_count = call_count + %s;" "$COUNT"
      printf "INSERT INTO external_api_call_caller (call_date, api, caller, call_count) VALUES (CURDATE(), '%s', '%s', %s) " "$API" "$CALLER" "$COUNT"
      printf "ON DUPLICATE KEY UPDATE call_count = call_count + %s;" "$COUNT"
    } | mysql_in || {
      # **성공 알림을 보내지 않고 끝낸다.** 여기서 계속 진행하면 아래 알림이 "집계에 반영했습니다"
      # 라고 잘못 말한다 — 실제로는 소비가 어디에도 안 남았고, 그건 이 도구가 막으려던 바로 그
      # 상태다. 실패는 실패로 알린다.
      echo "집계 기록 실패 — 앱의 한도 판단이 그만큼 낮게 남습니다" >&2
      notify "$(printf '## 외부 API 실측 집계 실패 — %s\n`%s` 가 %s콜을 썼지만 **집계에 남기지 못했습니다.**\n앱은 한도가 그만큼 남았다고 판단합니다 — DB·컨테이너 상태를 확인하고 손으로 반영하세요.' \
        "$API" "$CALLER" "$COUNT")"
      exit 1
    }

    AFTER=$(used_today)
    AFTER=${AFTER:-$((USED + COUNT))}
    LINE="오늘 누적 ${AFTER}"
    if [ "$LIMIT" -gt 0 ]; then
      LINE="$LINE/${LIMIT} ($((AFTER * 100 / LIMIT))%)"
    fi
    notify "$(printf '## 외부 API 실측 끝 — %s\n`%s` 가 %s콜을 썼습니다. 집계에 반영했습니다.\n%s' \
      "$API" "$CALLER" "$COUNT" "$LINE")"
    ;;
  *)
    echo "알 수 없는 동작: $ACTION (start|finish)" >&2
    exit 1
    ;;
esac

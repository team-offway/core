#!/usr/bin/env bash
# 새 서버에서 돈다 — <b>앱이 부팅하며 배치를 쐈는지</b>를 DB 로 확인한다(#576).
#
# **이것이 이사에서 가장 비싼 사고다.** `batch_run` 은 행 수가 열몇 개뿐이고 크기가 0MB 라
# "필요한 표만 골라 넣자" 는 순간 가장 먼저 사라진다. 그런데 그 표가 없으면 새 서버 첫 부팅에
# 배치가 전부 "아직 안 돌았다" 로 판정해 <b>한꺼번에 발사되고</b>, TourAPI·TMAP 일일 한도가
# 그날 통째로 마른다 — 사용자 요청이 먼저 죽는다.
#
# 행 수 대조(count-rows.sh)가 이미 그 표를 포함하지만, 여기서는 <b>앱을 띄운 뒤</b> 다시 본다.
# 복원은 잘 됐는데 부팅이 그것을 갈아엎는 경우가 남기 때문이다.
set -uo pipefail

CONTAINER=${MYSQL_CONTAINER:-offway-mysql}
DATABASE=${MYSQL_DATABASE_NAME:-offway}

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "MySQL 컨테이너($CONTAINER)가 떠 있지 않습니다." >&2
  exit 1
fi

# 비밀번호는 argv 가 아니라 `MYSQL_PWD` 로 넘긴다(같은 컨테이너의 `/proc` 노출을 막는다).
# DB 이름도 문자열 조립 대신 `$1` 로 넘긴다.
mysql_in() {
  docker exec -i "$CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B --default-character-set=utf8mb4 -uroot "$1"' \
    sh "$DATABASE"
}

echo "── 배치 실행 기록"
printf 'SELECT name, last_run_at FROM batch_run ORDER BY name;\n' | mysql_in | tr -d '\r'

# **비어 있으면 실패다.** 이 표가 없으면 모든 배치가 "한 번도 안 돌았다" 로 판정해 다음 주기에
# 한꺼번에 발사되고, 그날 TourAPI·TMAP 한도가 통째로 마른다 — 사용자 요청이 먼저 죽는다.
# 이 워크플로를 만드는 이유가 정확히 이 한 줄이다.
BATCHES=$(printf 'SELECT COUNT(*) FROM batch_run;\n' | mysql_in | tr -d '\r')
if ! [[ "$BATCHES" =~ ^[0-9]+$ ]]; then
  echo "::error::batch_run 을 읽지 못했습니다(값='$BATCHES')" >&2
  exit 1
fi
if [ "$BATCHES" -lt 1 ]; then
  echo "::error::batch_run 이 비어 있습니다 — 다음 주기에 배치가 전부 발사됩니다" >&2
  exit 1
fi
echo "(배치 기록 ${BATCHES}건)"

echo
echo "── 오늘 외부 API 호출"
# 오늘 호출이 갑자기 몇백 건이면 배치가 새로 발사된 것이다. 평소 하루치와 견줘 본다.
printf "SELECT api, call_count FROM external_api_call WHERE call_date = CURDATE() ORDER BY api;\n" \
  | mysql_in | tr -d '\r' || echo "(external_api_call 을 읽지 못했습니다 — 표 이름·칼럼을 확인하세요)"

echo
echo "── 표별 행 수"
bash "$(dirname "$0")/count-rows.sh"

#!/usr/bin/env bash
# 새 서버에서 돈다 — 표준입력의 gzip 덤프를 `offway` 에 넣는다(#576).
#
# **이 스크립트가 이 워크플로에서 유일하게 되돌릴 수 없는 일을 한다.** 그래서 덮어쓰기 전에
# 대상이 비어 있는지 직접 확인하고, 비어 있지 않으면 `MIGRATE_FORCE=1` 없이는 멈춘다.
# 워크플로 쪽 `confirm` 가드(새 호스트 이름을 손으로 다시 치기)와 함께 <b>이중</b>으로 막는다 —
# 옛 서버에 대고 복원하는 사고가 이 작업에서 가장 비싼 실수다.
#
# **SQL 은 stdin 으로 넘긴다.** `mysql -e "..."` 는 docker exec → sh -c → mysql 세 겹을 지나며
# 따옴표를 세 번 벗는데, 그 안에 백틱과 쌍따옴표가 섞인 DDL 이 들어가면 한 겹만 어긋나도 엉뚱한
# 문장이 실행된다. 그 대상이 `DROP DATABASE` 다.
set -uo pipefail

CONTAINER=${MYSQL_CONTAINER:-offway-mysql}
DATABASE=${MYSQL_DATABASE_NAME:-offway}
FORCE=${MIGRATE_FORCE:-0}

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "MySQL 컨테이너($CONTAINER)가 떠 있지 않습니다 — prepare 를 먼저 돌리세요." >&2
  exit 1
fi

# stdin 의 SQL 을 실행한다. 비밀번호는 컨테이너 자신의 환경변수라 호스트 `ps` 에 안 뜬다.
mysql_in() {
  docker exec -i "$CONTAINER" sh -c 'exec mysql -N -B --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD"'
}

if docker ps --format '{{.Names}}' | grep -qx offway-core; then
  # 앱이 이 DB 를 물고 있으면 복원 도중에 쓰기가 섞인다. 새 서버에서는 prepare 가 앱을 안 띄우므로
  # 보통은 없지만, 리허설을 두 번째 돌릴 때는 떠 있다.
  echo "앱 컨테이너가 떠 있습니다 — 복원 중 쓰기가 섞이지 않게 잠시 내립니다." >&2
  docker stop offway-core >/dev/null 2>&1 || true
  STOPPED_APP=1
fi

EXISTING=$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '%s' AND table_type = 'BASE TABLE';\n" "$DATABASE" \
  | mysql_in 2>/dev/null | tr -d '\r')

if ! [[ "$EXISTING" =~ ^[0-9]+$ ]]; then
  echo "대상 DB 상태를 확인하지 못했습니다(값='$EXISTING') — 덮어쓰지 않고 멈춥니다." >&2
  exit 1
fi

if [ "$EXISTING" -gt 0 ] && [ "$FORCE" != "1" ]; then
  echo "대상 $DATABASE 에 이미 표가 ${EXISTING}개 있습니다 — 덮어쓰지 않습니다." >&2
  echo "리허설을 다시 돌리는 것이라면 force 입력을 켜세요." >&2
  exit 1
fi

if [ "$EXISTING" -gt 0 ]; then
  echo "!! force — 기존 표 ${EXISTING}개를 버리고 DB 를 다시 만듭니다 !!" >&2
  # DROP 없이 덮어쓰면 덤프에 없는 표가 남아 스키마가 섞인다. 그 상태로 앱이 뜨면 Flyway 가
  # 자기가 모르는 표를 만나거나, 반대로 이미 적용된 버전을 다시 만나 애매하게 실패한다.
  printf 'DROP DATABASE IF EXISTS `%s`;\n' "$DATABASE" | mysql_in
fi

printf 'CREATE DATABASE IF NOT EXISTS `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n' "$DATABASE" | mysql_in

echo "복원 시작: $DATABASE" >&2

# **pipefail 이 없으면 잘린 덤프를 성공으로 읽는다.** gunzip 이 깨진 스트림에 실패해도 mysql 은
# 거기까지를 정상 입력으로 받아 0 으로 끝난다 — 절반만 들어간 DB 가 초록불이 된다.
set -o pipefail
gunzip \
  | docker exec -i "$CONTAINER" sh -c \
      "exec mysql --default-character-set=utf8mb4 -uroot -p\"\$MYSQL_ROOT_PASSWORD\" $DATABASE"
status=$?

if [ "${STOPPED_APP:-0}" = "1" ]; then
  # 내려놨던 앱을 다시 띄운다. 새 스키마로 붙어야 하므로 복원이 끝난 지금이 맞다.
  docker start offway-core >/dev/null 2>&1 || echo "앱을 다시 띄우지 못했습니다 — app 단계를 다시 도세요." >&2
fi

if [ "$status" -ne 0 ]; then
  echo "복원 실패(exit $status)" >&2
  exit "$status"
fi
echo "복원 완료" >&2

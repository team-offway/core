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

# stdin 의 SQL 을 실행한다.
#
# **비밀번호를 argv 로 넘기지 않는다.** `-p"$PW"` 는 컨테이너 안에서 확장되므로 호스트 `ps` 에는
# 안 뜨지만, 같은 컨테이너의 다른 프로세스가 `/proc/<pid>/cmdline` 을 읽으면 보인다. `MYSQL_PWD` 는
# argv 대신 환경으로 넘긴다 — 그 값은 이미 컨테이너 환경변수(`MYSQL_ROOT_PASSWORD`)로 있으므로
# 새로 노출되는 자리가 없다.
mysql_in() {
  docker exec -i "$CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B --default-character-set=utf8mb4 -uroot'
}

# **되돌릴 수 없는 일을 하기 전에, 되돌릴 수 있는 검사를 모두 끝낸다.**
#
# 예전에는 앱을 먼저 내리고 그다음에 force 를 판정했다. 그러면 force 없이 거부될 때 `exit 1` 이
# 아래 재시작 코드에 닿지 못해 **앱이 내려간 채 스크립트가 끝났다** — 복원을 거부한 것이 서비스를
# 멈추는 결과가 됐다. 검사는 전부 읽기라 앱이 떠 있어도 안전하다.
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

# 검사를 통과했으니 이제 앱을 내린다.
if docker ps --format '{{.Names}}' | grep -qx offway-core; then
  # 앱이 이 DB 를 물고 있으면 복원 도중에 쓰기가 섞인다. 새 서버에서는 prepare 가 앱을 안 띄우므로
  # 보통은 없지만, 리허설을 두 번째 돌릴 때는 떠 있다.
  echo "앱 컨테이너가 떠 있습니다 — 복원 중 쓰기가 섞이지 않게 잠시 내립니다." >&2
  # **중지 실패를 무시하지 않는다.** 무시하면 앱이 계속 쓰는 DB 를 DROP 하게 된다.
  if ! docker stop offway-core >/dev/null 2>&1; then
    echo "앱 컨테이너를 내리지 못했습니다 — 쓰기가 섞인 채 복원할 수 없어 멈춥니다." >&2
    exit 1
  fi
  STOPPED_APP=1
fi

# 복원을 시작하는 순간부터 이 DB 는 '완결되지 않은' 상태다. 마커를 먼저 지워, 중간에 죽어도
# app 단계가 그 DB 를 완성된 것으로 보지 않게 한다(워크플로 app 단계가 이 파일을 확인한다).
rm -f "$HOME/offway/.data-restored" 2>/dev/null || true

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
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot "$1"' \
      sh "$DATABASE"
status=$?

# **성공을 확인한 뒤에 앱을 띄운다.**
#
# 예전에는 이 판정보다 앞에서 무조건 띄웠다. 그러면 gunzip·mysql 이 실패해 **절반만 들어간 DB** 에
# 앱이 붙어 요청을 받고 Flyway 를 돌린다 — 복원 실패를 사용자가 먼저 만나게 된다.
if [ "$status" -ne 0 ]; then
  echo "복원 실패(exit $status) — 앱을 내려둔 채 멈춥니다." >&2
  if [ "${STOPPED_APP:-0}" = "1" ]; then
    echo "앱 컨테이너(offway-core)는 중지 상태입니다. DB 를 복구한 뒤 data 단계를 다시 도세요." >&2
  fi
  exit "$status"
fi

if [ "${STOPPED_APP:-0}" = "1" ]; then
  # 내려놨던 앱을 다시 띄운다. 새 스키마로 붙어야 하므로 복원이 끝난 지금이 맞다.
  docker start offway-core >/dev/null 2>&1 || echo "앱을 다시 띄우지 못했습니다 — app 단계를 다시 도세요." >&2
fi
echo "복원 완료" >&2

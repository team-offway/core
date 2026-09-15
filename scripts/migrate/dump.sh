#!/usr/bin/env bash
# 옛 서버에서 돈다 — `offway` 스키마를 gzip 으로 <b>표준출력에</b> 흘린다(#576).
#
# **파일로 떨구지 않는다.** 워크플로가 이 출력을 그대로 새 서버의 `restore.sh` 로 파이프하므로,
# 사용자 데이터가 러너 디스크에도 서버 디스크에도 남지 않는다. 남기면 그것을 지우는 일을 또
# 챙겨야 하고, 실패한 회차에서 가장 잘 잊힌다.
#
# **비밀번호가 프로세스 목록에 안 뜬다.** `docker exec ... sh -c` 안에서 컨테이너 자신의
# 환경변수를 참조하므로, 호스트에서 `ps` 를 쳐도 `-p...` 가 보이지 않는다. 러너를 거치지도 않는다.
#
# 진행 로그는 전부 stderr 로 보낸다 — stdout 은 덤프 전용이다. 한 줄이라도 섞이면 복원이 깨진다.
set -uo pipefail

CONTAINER=${MYSQL_CONTAINER:-offway-mysql}
DATABASE=${MYSQL_DATABASE_NAME:-offway}

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "MySQL 컨테이너($CONTAINER)가 떠 있지 않습니다." >&2
  exit 1
fi

echo "덤프 시작: $DATABASE" >&2

# --single-transaction: InnoDB 를 일관된 스냅샷으로 뜬다. 앱이 도는 중에도 테이블을 잠그지
#   않으므로 서비스가 멈추지 않는다. **다만 덤프가 시작된 뒤의 쓰기는 안 담긴다** — 본 이사에서
#   워크플로가 `freeze` 로 앱을 먼저 멈추는 이유다.
# --routines --triggers: 스키마만 옮기고 끝내면 트리거·프로시저가 조용히 사라진다.
# --set-gtid-purged=OFF: GTID 를 쓰지 않는 단일 인스턴스라 그 문장이 복원 쪽에서 오류만 낸다.
# --no-tablespaces: PROCESS 권한이 없으면 8.x 가 여기서 죽는다. 우리는 테이블스페이스를
#   따로 옮기지 않으므로 필요 없다.
#
# **파이프 중간 실패를 숨기지 않는다.** gzip 은 mysqldump 가 죽어도 정상 종료하므로,
# pipefail 이 없으면 **잘린 덤프가 성공으로 흘러간다** — 그러면 복원이 절반만 된 채 끝난다.
set -o pipefail
# **비밀번호를 argv 로 넘기지 않는다.** 컨테이너 안에서 확장되므로 호스트 `ps` 에는 안 뜨지만,
# 같은 컨테이너의 다른 프로세스가 `/proc/<pid>/cmdline` 을 읽으면 보인다. `MYSQL_PWD` 는 환경으로
# 넘기고, 그 값은 이미 컨테이너 환경변수로 있어 새로 노출되는 자리가 없다.
#
# DB 이름도 `$1` 로 넘긴다 — 문자열을 조립해 붙이면 이름에 공백·따옴표가 섞이는 날 엉뚱한 인자가 된다.
docker exec "$CONTAINER" sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --single-transaction --routines --triggers \
     --set-gtid-purged=OFF --no-tablespaces -uroot "$1"' \
  sh "$DATABASE" \
  | gzip -9
status=$?

if [ "$status" -ne 0 ]; then
  echo "덤프 실패(exit $status)" >&2
  exit "$status"
fi
echo "덤프 완료" >&2

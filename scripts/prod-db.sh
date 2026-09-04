#!/usr/bin/env bash
#
# 운영 MySQL 에 붙는다 — 인자를 주면 그 SQL 만 돌리고, 없으면 대화형 셸로 들어간다.
#
# 왜 필요했나: 운영 DB 는 컨테이너 안에만 있어 3306 이 호스트에 안 열려 있다(그게 맞는 상태다).
# 그래서 볼 때마다 ssh + docker exec + 비밀번호 환경변수 + 문자셋을 손으로 조립했는데,
# 따옴표가 세 겹으로 겹쳐 매번 틀렸다. `prod-logs.sh` 와 같은 이유로 한 줄로 만든다.
#
# 사용:
#   scripts/prod-db.sh                                  # 대화형 셸
#   scripts/prod-db.sh "SELECT COUNT(*) FROM users;"    # 한 번만 돌리고 끝
#   scripts/prod-db.sh -f query.sql                     # 파일에 담긴 SQL
#   scripts/prod-db.sh --root "SHOW COLUMNS FROM users;" # root 로 (기본은 offway 계정)
#
# 안전장치:
#   - 기본이 읽기 전용 계정(offway)이다. 스키마 조회처럼 권한이 필요할 때만 --root.
#   - INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER 가 보이면 한 번 물어본다.
#     운영 DB 는 EC2 도커 안의 MySQL 하나뿐이고 스케일아웃이 없다.
#
# 접속 전제 — 보안그룹에 내 IP 가 22번으로 열려 있어야 한다. 배포 워크플로는 러너 IP 만 잠깐 열고
# `always()` 로 회수하므로 평소에는 닫혀 있는 것이 정상이다. `Operation timed out` 이 뜨면 인증이
# 아니라 그쪽 문제다(pem 이 틀리면 `Permission denied`, 서버가 죽었으면 `Connection refused`).
set -euo pipefail

HOST=${OFFWAY_DB_HOST:-18.181.168.227}
LOGIN=${OFFWAY_DB_USER:-ubuntu}
KEY=${OFFWAY_DB_KEY:-$HOME/Downloads/offway-tokyo.pem}
CONTAINER=${OFFWAY_DB_CONTAINER:-offway-mysql}
SCHEMA=${OFFWAY_DB_SCHEMA:-offway}

if [ ! -r "$KEY" ]; then
  echo "ssh 키를 못 읽습니다: $KEY" >&2
  echo "OFFWAY_DB_KEY 로 경로를 지정하세요." >&2
  exit 1
fi

# 계정 고르기. 기본은 앱 계정 — 실수로 쓰기를 날려도 범위가 좁다.
DB_USER_ENV='$MYSQL_USER'
DB_PASS_ENV='$MYSQL_PASSWORD'
if [ "${1:-}" = "--root" ]; then
  DB_USER_ENV='root'
  DB_PASS_ENV='$MYSQL_ROOT_PASSWORD'
  shift
fi

SQL=""
if [ "${1:-}" = "-f" ]; then
  [ -r "${2:-}" ] || { echo "SQL 파일을 못 읽습니다: ${2:-}" >&2; exit 1; }
  SQL=$(cat "$2")
elif [ $# -gt 0 ]; then
  SQL="$*"
fi

# 쓰기로 보이면 한 번 묻는다. 완벽한 판별이 아니라 실수를 늦추는 턱이다.
if [ -n "$SQL" ] && printf '%s' "$SQL" | grep -Eiq '\b(insert|update|delete|drop|truncate|alter|create|grant)\b'; then
  echo "⚠️  쓰기로 보이는 구문이 있습니다. 운영 DB 는 하나뿐이고 스케일아웃이 없습니다." >&2
  printf '%s\n' "$SQL" >&2
  printf '계속할까요? (yes 를 정확히 입력) ' >&2
  read -r answer
  [ "$answer" = "yes" ] || { echo "취소했습니다." >&2; exit 1; }
fi

# --default-character-set=utf8mb4 를 빼면 한글이 ??? 로 나온다.
# 비밀번호는 컨테이너 안 환경변수라 이 셸에도, 프로세스 목록에도 안 남는다.
remote_mysql() {
  printf 'docker exec %s %s sh -c %s' \
    "$1" "$CONTAINER" \
    "'mysql -u$DB_USER_ENV -p\"$DB_PASS_ENV\" --default-character-set=utf8mb4 $SCHEMA'"
}

if [ -z "$SQL" ]; then
  # 대화형 — -it 로 tty 를 넘긴다.
  exec ssh -i "$KEY" -o ConnectTimeout=10 -t "$LOGIN@$HOST" "$(remote_mysql -it)"
fi

# 단발 실행 — SQL 은 stdin 으로 넘긴다. 인자로 넣으면 따옴표가 또 겹친다.
printf '%s\n' "$SQL" | ssh -i "$KEY" -o ConnectTimeout=10 "$LOGIN@$HOST" "$(remote_mysql -i)" 2>&1 \
  | grep -v 'Using a password on the command line'

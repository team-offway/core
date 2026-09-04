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
#   - **조회가 아닌 구문이면 한 번 물어본다.** 위험한 낱말을 나열하는 대신 조회로 인정할 것만
#     추려 두고, 나머지는 전부 묻는 쪽에 떨어뜨린다(fail closed).
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

# 읽기 전용으로 인정하는 구문의 첫 낱말. **이 목록에 없으면 묻는다.**
#
# 처음에는 반대로 짰다 — 위험한 낱말(insert·update·drop…)을 나열해 그게 보이면 묻는 방식이었다.
# 그 방식은 늘 빠지는 게 생긴다. 실제로 `RENAME TABLE`(서비스 접근이 끊긴다)과 `REPLACE INTO`
# (기존 행을 지우고 다시 넣는다)가 통째로 빠져 확인 없이 실행됐다. 하나를 더해도 다음 것이 또 빠진다.
#
# 그래서 뒤집는다. 이 스크립트로 하는 일은 사실상 조회뿐이라 인정 목록이 짧고, 모르는 구문은
# 자동으로 묻는 쪽에 떨어진다(fail closed). 판별이 완벽할 필요가 없어지는 것이 요점이다.
READ_ONLY_HEAD='select|show|describe|desc|explain|with'

# 그래도 한 겹 더 둔다 — MySQL 8 은 `WITH cte AS (...) DELETE FROM ...` 를 허용해서,
# 첫 낱말만 보면 CTE 뒤에 숨은 쓰기를 놓친다.
WRITE_VERBS='insert|update|delete|replace|drop|truncate|alter|create|rename|grant|revoke|load|call|set|lock|flush|kill|optimize|repair|analyze|handler'

# 주석을 걷어낸다. `-- 여기서 drop 하면 안 된다` 같은 설명에 헛되이 걸리지 않게, 그리고
# 주석 뒤에 이어 붙인 구문을 놓치지 않게.
strip_comments() {
  sed -e 's:/\*[^*]*\*/: :g' -e 's/--[[:space:]].*$//' -e 's/#.*$//'
}

needs_confirmation() {
  cleaned=$(printf '%s\n' "$1" | strip_comments)

  # 구문마다 첫 낱말을 뽑는다. 여는 괄호는 건너뛴다 — `(SELECT ...) UNION ...` 도 조회다.
  heads=$(printf '%s\n' "$cleaned" | tr ';' '\n' \
            | sed -e 's/^[[:space:](]*//' \
            | grep -oE '^[A-Za-z_]+' | tr '[:upper:]' '[:lower:]')

  # 첫 낱말이 조회가 아닌 구문이 하나라도 있으면 묻는다.
  if printf '%s\n' "$heads" | grep -v '^$' | grep -qvE "^($READ_ONLY_HEAD)$"; then
    return 0
  fi
  # CTE 안에 숨은 쓰기.
  printf '%s' "$cleaned" | grep -qiE "\b($WRITE_VERBS)\b"
}

if [ -n "$SQL" ] && needs_confirmation "$SQL"; then
  echo "조회가 아닌 구문이 있습니다. 운영 DB 는 하나뿐이고 스케일아웃이 없습니다." >&2
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

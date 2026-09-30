#!/usr/bin/env bash
# 어느 서버에서든 돈다 — `offway` 의 <b>모든</b> 표와 행 수를 `표<TAB>행수` 로 찍는다(#576).
#
# **표 목록을 손으로 적지 않는다.** 이슈는 "이 표들을 보자" 고 목록을 뒀는데, 목록은 표가 늘 때
# 같이 늘지 않는다 — 다음에 생기는 표가 가장 조용히 빠진다. 그리고 애초에 이 워크플로를 만드는
# 이유가 <b>사람이 목록에서 빠뜨리는 것</b>이었다. 전부 세면 그 위험이 통째로 사라진다.
#
# **`information_schema.table_rows` 를 쓰지 않는다.** InnoDB 에서 그 값은 <b>추정치</b>라 같은
# 데이터에서도 양쪽이 다르게 나온다. 대조가 목적인데 대조할 수 없는 숫자를 쓸 이유가 없다.
# 표마다 `COUNT(*)` 를 센다 — 느리지만 이 작업은 1년에 몇 번이다.
#
# stdout 은 대조용 데이터 전용이다. 진행 로그는 stderr 로 보낸다.
set -uo pipefail

CONTAINER=${MYSQL_CONTAINER:-offway-mysql}
DATABASE=${MYSQL_DATABASE_NAME:-offway}

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "MySQL 컨테이너($CONTAINER)가 떠 있지 않습니다." >&2
  exit 1
fi

# **비밀번호를 argv 로 넘기지 않는다.** 컨테이너 안에서 확장되므로 호스트 `ps` 에는 안 뜨지만,
# 같은 컨테이너의 다른 프로세스가 `/proc/<pid>/cmdline` 을 읽으면 보인다. `MYSQL_PWD` 로 넘기면
# argv 에 안 남고, 그 값은 이미 컨테이너 환경변수로 있어 새로 노출되는 자리가 없다.
#
# **기본 DB 를 지정하지 않는다.** 복원 전 새 서버에는 `offway` 가 아직 없어, 지정하면 접속 자체가
# 실패한다. 대신 아래 두 질의가 DB 를 <b>문장 안에서 수식</b>한다.
mysql_in() {
  docker exec -i "$CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B --default-character-set=utf8mb4 -uroot'
}

# 한 번에 만들고 한 번에 실행한다. 표마다 docker exec 를 부르면 왕복이 표 수만큼 곱해진다.
#
# 표가 하나도 없으면 UNION 문장이 빈 문자열이 되어 mysql 이 문법 오류를 낸다 — 그건 "빈 DB" 라는
# 정상 상태이므로 먼저 가른다(복원 전 새 서버가 그렇다).
#
# **`FROM` 을 DB 로 수식한다.** 예전에는 `FROM \`table\`` 만 생성해, 기본 DB 없이 접속하는 이
# 스크립트에서 두 번째 질의가 `No database selected` 로 통째로 실패했다 — 행 수 대조가 이 워크플로의
# 핵심 안전장치인데 한 번도 동작하지 않았다. 접속에 DB 를 붙이는 대신 문장을 수식하는 이유는
# `mysql_in` 주석에 적었다(복원 전에는 그 DB 가 없다).
COUNT_SQL=$(printf "SELECT CONCAT('SELECT ''', table_name, ''' AS t, COUNT(*) AS c FROM \`%s\`.\`', table_name, '\` UNION ALL ') FROM information_schema.tables WHERE table_schema = '%s' AND table_type = 'BASE TABLE' ORDER BY table_name;\n" "$DATABASE" "$DATABASE" \
  | mysql_in 2>/dev/null | tr -d '\r')

if [ -z "$COUNT_SQL" ]; then
  echo "표가 없습니다: $DATABASE" >&2
  exit 0
fi

# 생성된 조각은 한 줄에 하나씩이고 저마다 " UNION ALL " 로 끝난다. 줄바꿈은 SQL 이 무시하므로
# **마지막 줄의 꼬리만** 떼면 그대로 한 문장이 된다.
FINAL_SQL=$(printf '%s\n' "$COUNT_SQL" | sed '$ s/ UNION ALL $//')

printf '%s;\n' "$FINAL_SQL" | mysql_in | tr -d '\r' | sort

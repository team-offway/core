#!/usr/bin/env bash
# 운영 MySQL 을 띄운다 — <b>이 파일이 정본이다</b>(#576).
#
# **왜 이제야 생겼나.** 앱의 `docker run` 은 `recover-container.sh` 에, Caddy 의 것은
# `setup-https.md` 에 박혀 있는데 **MySQL 만 아무 데도 없었다.** 서버에 떠 있는 컨테이너가
# 유일한 근거였고, 그 인스턴스가 죽으면 똑같이 되살릴 방법이 없었다. 이사와 무관하게 필요했던
# 것이고, 이사 워크플로가 이 파일을 쓰는 첫 자리다.
#
# 서버에서 확인한 실제 모양을 그대로 옮겼다 — 이미지·재시작 정책·볼륨·바인드·환경파일.
#
# ## 쓰는 법
#
#   bash start-mysql.sh                 # ~/offway 기준
#   OFFWAY_HOME=/path bash start-mysql.sh
#
# ## `conf.d` 는 왜 레포에 없나
#
# `~/offway/mysql/conf.d` 의 내용은 **지금 서버에만 있다.** 짐작해서 레포에 써 넣으면 그 짐작이
# 곧 운영 설정이 된다 — buffer pool 크기 하나만 어긋나도 새 서버가 다르게 돈다. 그래서
# `migrate.yml` 의 `prepare` 가 **옛 서버에서 새 서버로 그대로 옮기고, 내용을 실행 로그에 찍는다.**
# 그 출력을 보고 나면 이 레포의 `scripts/mysql/conf.d/` 로 커밋하면 된다. 그때 이 스크립트도
# 그 디렉터리를 올리도록 고친다.
#
# ## 이 스크립트가 하지 않는 것
#
# **이미 떠 있으면 아무것도 안 한다.** 운영 DB 를 다시 만드는 일은 스크립트가 알아서 할 일이
# 아니다 — 볼륨이 살아 있으면 데이터는 그대로지만, 그 판단은 사람이 한다.
set -uo pipefail

OFFWAY_HOME=${OFFWAY_HOME:-$HOME/offway}
CONTAINER=offway-mysql
NETWORK=offway-net

# 8.4 LTS. 운영에 떠 있는 그 버전이고, 테스트(Testcontainers)도 같은 계열을 쓴다.
# 올릴 때는 여기와 테스트 쪽을 함께 본다.
IMAGE=mysql:8.4

# 이름 붙은 볼륨이다. 컨테이너를 지워도 데이터가 남는 유일한 이유가 이것이다 —
# 바인드 마운트로 바꾸면 호스트 파일시스템 권한 문제가 따라온다.
DATA_VOLUME=offway-mysql-data

# 3306 을 **호스트로 노출하지 않는다.** 앱이 같은 도커 네트워크 안에서 컨테이너 이름으로 붙으므로
# (DB_URL 의 호스트가 offway-mysql 인 이유다) 바깥에서 DB 에 직접 닿을 길이 없다.
# 여기에 -p 를 더하는 순간 그 성질이 사라진다.

if [ ! -f "$OFFWAY_HOME/mysql.env" ]; then
  echo "환경 파일이 없습니다: $OFFWAY_HOME/mysql.env" >&2
  echo "MYSQL_ROOT_PASSWORD · MYSQL_DATABASE · MYSQL_USER · MYSQL_PASSWORD 가 필요합니다." >&2
  exit 1
fi

if [ ! -d "$OFFWAY_HOME/mysql/conf.d" ]; then
  echo "설정 디렉터리가 없습니다: $OFFWAY_HOME/mysql/conf.d" >&2
  echo "비어 있어도 되지만 디렉터리는 있어야 합니다(바인드 마운트 대상)." >&2
  exit 1
fi

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "$CONTAINER 가 이미 떠 있습니다 — 아무것도 하지 않습니다."
  exit 0
fi

docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK"

# 멈춰 있는 동명 컨테이너가 있으면 치운다. 볼륨은 그대로라 데이터는 남는다.
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true

docker run -d \
  --name "$CONTAINER" \
  --network "$NETWORK" \
  --restart unless-stopped \
  -v "$DATA_VOLUME:/var/lib/mysql" \
  -v "$OFFWAY_HOME/mysql/conf.d:/etc/mysql/conf.d" \
  --env-file "$OFFWAY_HOME/mysql.env" \
  "$IMAGE"

# 떴다고 받을 준비가 된 것은 아니다. 첫 기동은 데이터 디렉터리를 만드느라 수십 초 걸리고,
# 그 사이에 앱을 붙이면 Flyway 가 연결 실패로 죽는다.
echo "기동 대기 중..."
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER" sh -c 'mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1; then
    echo "$CONTAINER 준비 완료"
    exit 0
  fi
  sleep 2
done

echo "120초 동안 응답이 없습니다 — docker logs $CONTAINER 를 확인하세요" >&2
exit 1

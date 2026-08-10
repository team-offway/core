#!/usr/bin/env bash
#
# 운영 컨테이너 로그를 붙어서 본다 — `logfmt.py` 로 색을 입혀 내보낸다.
#
# 왜 필요했나: 대표 사진이 안 바뀐 적이 있었는데(2026-08-09), 배포는 성공이고 API 는 200 이라 화면만으로는
# 원인을 못 갈랐다. 서버 로그의 적재 완료 한 줄이면 끝날 일이었다. 그걸 매번 ssh 명령을 조립해서 보는 대신
# 한 줄로 붙는다.
#
# 사용:
#   scripts/prod-logs.sh                      # 최근 200줄 + 계속 따라가기
#   scripts/prod-logs.sh --level WARN         # WARN 이상만
#   scripts/prod-logs.sh --grep 갤러리         # 메시지로 거르기
#   scripts/prod-logs.sh --trace a1b2c3       # 요청 하나만
#   OFFWAY_LOG_TAIL=2000 scripts/prod-logs.sh # 더 거슬러 올라가기
#
# 인자는 그대로 logfmt.py 로 넘어간다(`logfmt.py --help` 참고).
#
# 접속 전제 — 보안그룹에 내 IP 가 22번으로 열려 있어야 한다. 배포 워크플로는 러너 IP 만 잠깐 열고
# `always()` 로 회수하므로, 평소에는 닫혀 있는 것이 정상이다. `Operation timed out` 이 뜨면 인증이 아니라
# 그쪽 문제다(pem 이 틀리면 `Permission denied`, 서버가 죽었으면 `Connection refused`).
set -euo pipefail

HOST=${OFFWAY_LOG_HOST:-18.181.168.227}
LOGIN=${OFFWAY_LOG_USER:-ubuntu}
KEY=${OFFWAY_LOG_KEY:-$HOME/Downloads/offway-tokyo.pem}
CONTAINER=${OFFWAY_LOG_CONTAINER:-offway-core}
TAIL=${OFFWAY_LOG_TAIL:-200}

if [ ! -r "$KEY" ]; then
  echo "ssh 키를 못 읽습니다: $KEY" >&2
  echo "OFFWAY_LOG_KEY 로 경로를 지정하세요." >&2
  exit 1
fi

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

# ServerAliveInterval — 조용한 새벽에 붙여두면 중간 장비가 idle 연결을 끊는다. 30초마다 깨워 유지한다.
# 2>&1 은 원격 셸 안에서 붙인다. 컨테이너가 stderr 로 흘리는 WARN·스택을 함께 받아야 하기 때문이다.
exec ssh -i "$KEY" \
  -o ConnectTimeout=10 \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  "$LOGIN@$HOST" \
  "docker logs -f --tail $TAIL $CONTAINER 2>&1" \
  | python3 "$HERE/logfmt.py" "$@"

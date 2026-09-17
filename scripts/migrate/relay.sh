#!/usr/bin/env bash
# 원격 서버가 찍은 진행 표시를 디스코드로 중계한다(#576).
#
# ## 왜 필요한가
#
# 단계 내부에서 오래 걸리는 것들(도커 이미지 빌드, MySQL 초기화, 앱 헬스체크 5분)은 **서버 안에서**
# 일어난다. 그 블록은 `ssh newsrv 'bash -s' <<REMOTE` 한 덩어리라, 진행을 알리려면 원격 안에서
# 알림을 보내야 하는데 서버에는 웹훅도 notify.sh 도 없다.
#
# 원격 블록을 스텝 여러 개로 쪼개면 알릴 자리는 생기지만, 접속이 그만큼 늘고 `cd`·변수 같은
# 원격 상태가 스텝 사이에서 끊긴다.
#
# 그래서 **원격은 표식만 찍고, 러너가 읽어서 보낸다.** 원격 코드는 `echo ::sub::<한 줄>` 한 줄만
# 더하면 되고, 그 줄은 Actions 로그에도 그대로 남는다.
#
# ## 출력을 삼키지 않는다
#
# 읽은 줄을 **전부 그대로 다시 찍는다.** 중계가 로그를 먹으면 실패했을 때 볼 것이 사라진다.
#
# ## 종료코드
#
# 이 스크립트는 항상 0 이다. 원격의 성패는 파이프 앞쪽이 들고 있으므로 호출부가 `set -o pipefail`
# 로 받는다 — 중계가 성패를 판정하지 않는다.
#
# 사용법:
#   ssh newsrv 'bash -s' <<'REMOTE' | bash scripts/migrate/relay.sh "2/5"
#   ...
#   echo "::sub::MySQL 기동 완료"
#   REMOTE
set -uo pipefail

NO=${1:-}
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

while IFS= read -r line; do
  printf '%s\n' "$line"
  case "$line" in
    # notify.sh 의 출력을 버리지 않는다 — 웹훅 실패 경고가 여기서 사라지면 "알림이 안 왔다" 를
    # 아무도 설명할 수 없다. 전송 성공 줄이 로그에 섞이는 값은 그보다 싸다.
    '::sub::'*) bash "$HERE/notify.sh" sub "$NO" "${line#::sub::}" || true ;;
  esac
done
exit 0

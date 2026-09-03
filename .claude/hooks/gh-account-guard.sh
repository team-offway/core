#!/bin/sh
# gh 를 이 레포에서 항상 같은 계정으로 쓰게 만든다.
#
# 왜 훅인가 — 문서로는 두 번 새어 나갔다. `gh` 의 활성 계정이 예고 없이 회사 계정(theo-s-park)으로
# 바뀌어 있었고, 그 계정으로 만든 이슈·리뷰 답글이 **회사 메일로 알림을 보냈다.** 게다가 그 계정은
# 이 레포 쓰기 권한이 없어 `gh issue create` 가 **이슈를 만든 뒤 assignee 단계에서만 실패한다** —
# 에러만 보고 재시도하면 유령 이슈가 남는다(실제로 #408·#412 가 그렇게 남았다).
#
# 두 모드로 쓴다.
#   --session : SessionStart 에서 실효 계정을 한 번 실측한다(네트워크 1회).
#   (인자 없음) : PreToolUse 에서 gh 호출을 가로채 GH_TOKEN 고정 여부만 본다(로컬, 빠름).
#
# 고정 방법은 `.claude/settings.local.json` 의 env.GH_TOKEN 이다(gitignore 대상).
# 그 값이 있으면 keyring 의 활성 계정과 무관하게 gh 가 그 토큰을 쓴다.

EXPECTED_LOGIN="sevineleven"
SETUP_HINT="고치는 법: gh auth switch --user ${EXPECTED_LOGIN} 로 바꾸고,
  .claude/settings.local.json 의 env.GH_TOKEN 을 그 계정 토큰(gh auth token --user ${EXPECTED_LOGIN})으로 채운다."

# ── SessionStart: 실효 계정을 실측한다 ──────────────────────────────────
if [ "$1" = "--session" ]; then
    # 네트워크가 없거나 gh 가 없으면 세션을 막지 않는다 — 이 훅은 계정을 가리는 장치이지
    # 오프라인 작업을 금지하는 장치가 아니다.
    command -v gh >/dev/null 2>&1 || exit 0
    login=$(gh api user --jq .login 2>/dev/null) || exit 0
    [ -n "$login" ] || exit 0
    if [ "$login" != "$EXPECTED_LOGIN" ]; then
        printf '%s\n' "gh 활성 계정이 '${login}' 입니다. 이 레포는 '${EXPECTED_LOGIN}' 로만 씁니다." >&2
        printf '%s\n' "그 계정으로 만든 이슈·코멘트는 회사 메일로 알림이 가고, 쓰기 권한이 없어 절반만 성공합니다." >&2
        printf '%s\n' "$SETUP_HINT" >&2
        exit 2
    fi
    exit 0
fi

# ── PreToolUse: gh 호출 앞에서 고정 여부를 본다 ─────────────────────────
# stdin 으로 도구 입력 JSON 이 온다. 명령 문자열만 꺼내 gh 호출인지 가린다.
# 통째로 grep 하지 않는 이유는, 파일 내용이나 설명에 들어 있는 "gh " 로 엉뚱한 명령을 막지 않기 위해서다.
payload=$(cat)
command_text=$(printf '%s' "$payload" | python -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
print(data.get("tool_input", {}).get("command", ""))
' 2>/dev/null)

# 명령을 못 읽었으면 통과시킨다. 읽지 못한 것을 위반으로 단정하면 훅이 무시당한다.
[ -n "$command_text" ] || exit 0

# gh 호출이 아니면 볼 것이 없다. 낱말 단위로 본다 — "highlight" 같은 단어에 걸리지 않게.
printf '%s' "$command_text" | grep -qE '(^|[|&;( 	])gh([ 	]|$)' || exit 0

if [ -z "$GH_TOKEN" ]; then
    printf '%s\n' "gh 를 부르려는데 GH_TOKEN 이 없습니다 — 활성 계정이 무엇인지 보장되지 않습니다." >&2
    printf '%s\n' "이 레포는 '${EXPECTED_LOGIN}' 로만 씁니다. 다른 계정으로 나가면 회사 메일로 알림이 가고," >&2
    printf '%s\n' "쓰기 권한이 없어 'gh issue create' 가 이슈를 만든 뒤에만 실패해 유령 이슈가 남습니다." >&2
    printf '%s\n' "$SETUP_HINT" >&2
    exit 2
fi

exit 0

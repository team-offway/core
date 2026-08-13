#!/bin/sh
# PostToolUse 컨벤션 가드 — 편집된 파일에 금지 패턴이 있으면 exit 2 로 차단한다.
#
# 여기 담는 것은 "정규식으로 100% 판정 가능하고, 위반이 곧 버그" 인 규칙뿐이다.
# 매직 값·rich domain·DIP·다형성처럼 판단이 필요한 것은 훅이 아니라
# `/pre-pr` 의 self-audit 이 담당한다 (오탐이 나면 훅 자체가 무시당한다).
#
# 테스트: sh .claude/hooks/convention-check.sh --file <경로>

set -u

# 파싱에 실패하면 통과시키지 않고 막는다(fail closed).
# 실측: 이 가드가 없을 때 깨진 payload 를 주면 FILE 이 비고 exit 0 으로 빠져
# 모든 컨벤션 검사가 조용히 건너뛰어졌다. 훅이 꺼진 줄 모르는 상태가 가장 나쁘다.
abort() {
    printf '컨벤션 훅을 실행할 수 없습니다 — %s\n' "$1" >&2
    printf '훅이 조용히 꺼지지 않도록 통과 대신 차단합니다. 원인을 고친 뒤 다시 시도하세요.\n' >&2
    exit 2
}

if [ "${1:-}" = "--file" ]; then
    FILE="${2:-}"
    [ -n "$FILE" ] || abort "--file 뒤에 경로가 없습니다"
else
    command -v jq >/dev/null 2>&1 || abort "jq 가 설치돼 있지 않습니다"
    INPUT=$(cat)
    FILE=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty' 2>/dev/null) \
        || abort "훅 입력(JSON) 파싱 실패"
    [ -n "$FILE" ] || abort "훅 입력에서 tool_input.file_path 를 찾지 못했습니다"
fi

# 파일이 실제로 없는 것은 정상이다 — 삭제·이동 직후의 편집 이벤트가 여기 온다.
[ -f "$FILE" ] || exit 0

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0

# 레포 루트 기준 상대 경로로 정규화 (Windows 백슬래시 포함)
REL=$(printf '%s' "$FILE" | tr '\\' '/')
ROOT_SLASH=$(printf '%s' "$ROOT" | tr '\\' '/')
REL=${REL#"$ROOT_SLASH"/}

# $1 = 위반 제목, $2 = 근거·해법 설명, 이어서 grep 결과
fail() {
    title=$1
    shift
    printf '컨벤션 위반 — %s\n\n' "$title" >&2
    printf '%s\n' "$@" >&2
    exit 2
}

# 패턴에 걸리면 차단. $1=파일 $2=정규식 $3=제목 $4=설명
deny() {
    hits=$(grep -nE "$2" "$1" 2>/dev/null) || return 0
    fail "$3" "$4" "" "$hits"
}

# ---------------------------------------------------------------- Flyway
case "$REL" in
src/main/resources/db/migration/*.sql)
    # 이미 커밋된 마이그레이션인가 (HEAD 에 존재하면 배포됐다고 본다)
    #
    # repeatable(R__)은 제외한다. 내용이 바뀌면 Flyway 가 다시 실행하는 것이 그 파일의 설계 목적이라,
    # 수정이 곧 사용법이다. 실제로 정책·태그 시드가 해마다 바뀌어서 R__ 로 둔 것인데, 여기서 막으면
    # 그 시드를 영영 못 고친다. checksum 이 깨져 부팅이 실패하는 것은 versioned(V__) 뿐이다.
    case "${REL##*/}" in R__*) IS_REPEATABLE=1 ;; *) IS_REPEATABLE=0 ;; esac
    if [ "$IS_REPEATABLE" -eq 0 ] && git cat-file -e "HEAD:$REL" 2>/dev/null; then
        if ! git diff --quiet -- "$REL" 2>/dev/null; then
            fail "적용된 Flyway 마이그레이션 수정" \
                "$REL 은 이미 커밋된 마이그레이션입니다." \
                "수정하면 Flyway checksum 이 깨져 부팅이 실패합니다." \
                "forward-only — 새 timestamp 로 보정 마이그레이션을 추가하세요."
        fi
    fi

    # UNIQUE·PRIMARY KEY 제약은 정상이므로 FOREIGN KEY 만 정확히 잡는다
    deny "$FILE" 'FOREIGN[[:space:]]+KEY' \
        "마이그레이션에 FOREIGN KEY 제약" \
        "FK 는 Flyway 의 additive·out-of-order·forward-only 규칙과 상충합니다.
참조 무결성은 서비스 계층이 책임집니다. 조회 인덱스(KEY idx_*)는 그대로 두세요."
    ;;
esac

# ---------------------------------------------------------------- Java 공통
case "$REL" in
*.java)
    # javax.sql·javax.crypto·javax.net 등은 JDK 표준이라 그대로 둔다.
    # Jakarta EE 로 옮겨간 5개만 잡는다.
    deny "$FILE" 'import[[:space:]]+javax\.(persistence|validation|servlet|annotation|transaction)\.' \
        "javax 네임스페이스" \
        "Spring Boot 4 는 Jakarta EE 입니다. import 를 jakarta.* 로 바꾸세요.
(javax.sql·javax.crypto 등 JDK 표준은 그대로 두면 됩니다.)"

    deny "$FILE" 'FetchType\.EAGER|fetch[[:space:]]*=[[:space:]]*EAGER' \
        "EAGER 페치" \
        "default LAZY 를 유지하세요. N+1 은 fetch join·@EntityGraph·@BatchSize 로 차단합니다."

    deny "$FILE" 'HttpStatus\.NO_CONTENT|\.noContent\(\)' \
        "HTTP 204 사용" \
        "ApiResponseBody 래퍼가 항상 body 를 만들므로 204 와 충돌합니다.
내릴 데이터가 없으면 200 + ApiResponseBody.ok() (data=null) 로 응답하세요."
    ;;
esac

# ---------------------------------------------------------------- domain 레이어
case "$REL" in
*/domain/*.java)
    deny "$FILE" '^[[:space:]]*@(Setter|Data)\b|public[[:space:]]+void[[:space:]]+set[A-Z]' \
        "도메인에 public setter" \
        "필드는 private, public setter 는 금지입니다 (캡슐화·rich domain).
상태 변경은 의미 있는 도메인 메서드로 표현하고, 생성은 static 팩토리·빌더로 하세요."
    ;;
esac

# ---------------------------------------------------------------- controller 레이어
case "$REL" in
*/controller/*.java)
    deny "$FILE" '@Transactional' \
        "컨트롤러에 @Transactional" \
        "트랜잭션 경계는 서비스 메서드 레벨입니다."
    ;;
esac

# ---------------------------------------------------------------- 테스트
case "$REL" in
src/test/*.java)
    # @TestConfiguration 은 금지 대상이 아니다 — 외부 port stub 등록에 필요하다
    # (testing-convention: "@TestConfiguration + @Primary 로 등록").
    deny "$FILE" '@(MockBean|SpyBean)\b' \
        "내부 컴포넌트 모킹" \
        "내부 컴포넌트는 실제 빈으로 통합 테스트합니다.
외부 호출 경계만 port 인터페이스의 프로그래머블 stub 으로 격리하세요
(@TestConfiguration + @Primary, default 람다는 throw)."

    deny "$FILE" '@(DirtiesContext|ActiveProfiles|TestPropertySource)\b' \
        "컨텍스트 캐시 파괴" \
        "통합 테스트는 단일 컨텍스트를 공유합니다.
이 어노테이션들은 컨텍스트를 새로 띄워 전체 테스트 시간을 늘립니다."
    ;;
esac

exit 0

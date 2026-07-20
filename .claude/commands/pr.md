브랜치에서 작업한 내용을 STAR 구조 PR 로 정리하여 GitHub 에 올립니다. 이미 PR 이 있으면 본문 STAR 를 최종 상태로 직접 갱신합니다 (살아있는 본문 — `3-B`). assignee(`@me`)·라벨(연관 이슈에서 복사, 없으면 브랜치 prefix)도 자동 설정합니다.

> **실행 환경**: 이 문서의 셸 블록은 **Bash 도구**로 실행한다 (PowerShell 아님). `$(...)`·배열·heredoc 을 쓰기 때문이다.

## PR 본문 작성 원칙

**대화 컨텍스트가 핵심이다.** diff 요약이 아니라, 이번 세션에서 나눈 고민·트레이드오프·결정 이유가 PR 의 가치다.

**맥락은 담되 산문으로 늘어놓지 않는다.** 맥락이 많을수록 산문은 길어져 가독성이 떨어진다. "무엇을 담을지(맥락)" 는 유지하고 "어떻게 보일지(구조)" 를 더한다 — 대조·비교 정보는 표로, 동작 규칙은 굵은 키워드 불릿으로.

## 절차

### 0단계: 작업 위치 가드 + 모드 결정 + base 감지

**0-A. 작업 위치 가드 (워크트리 감지)** — `/pr` 의 모든 git·gh 명령은 현재 작업 디렉터리 기준으로 돈다. 워크트리에서 작업해놓고 메인 체크아웃(base 브랜치)에서 `/pr` 을 부르면 조용히 틀린 PR(또는 "변경 없음")이 만들어진다. 이를 먼저 거른다.

```bash
CURRENT_BRANCH=$(git branch --show-current)
# origin 최신화 — base 판정과 1단계 log·diff 가 전부 origin/$BASE 기준이라, fetch 없이는 stale 참조로 남의 커밋이 diff 에 섞인다.
git fetch origin -q || { echo "git fetch 실패 — origin 이 stale 인 채 진행하지 않는다."; exit 1; }
# 진입 정리: 7일 넘게 안 건드린 stale PR 본문 임시파일 제거 (session-close 를 안 거친 중단 작업의 누수 회수).
# 임시파일은 지워져도 gh pr view 로 재생성돼 손실이 없다. 진행 중 장기 PR 을 안 건드리도록 임계값을 넉넉히 둔다.
find /tmp -maxdepth 1 -name 'pr_body_*.md' -mmin +10080 -delete 2>/dev/null
BASE_GUESS=dev
echo "CURRENT_BRANCH=$CURRENT_BRANCH BASE_GUESS=$BASE_GUESS"
```

`$CURRENT_BRANCH` 가 `$BASE_GUESS` 와 **다르면** 정상(작업 브랜치) — 0-B 로 넘어간다.

`$CURRENT_BRANCH` 가 `$BASE_GUESS` 와 **같으면** PR 을 올릴 작업 브랜치가 아니다:

```bash
git worktree list --porcelain   # base 가 아닌 브랜치를 가진 워크트리 = 작업 후보
```

- **작업 후보 워크트리가 있으면** `AskUserQuestion` (single-select) 으로 "그 워크트리로 진입해 `/pr` 을 이어갈까요?" 를 묻는다 (**진입 = Recommended, 첫 번째**). 후보가 여럿이면 각 워크트리(경로 + 브랜치)를 옵션으로 나열한다.
  - **진입 동의** → `EnterWorktree` 를 `path={선택한 워크트리 경로}` 로 호출한 뒤 **0단계를 처음부터 다시 시작**한다.
  - **거부** → 멈춘다. "작업 워크트리에서 직접 `/pr` 을 불러주세요" 안내.
- **작업 후보 워크트리가 없으면** base 브랜치에서 `/pr` 을 부른 셈이라 올릴 작업 브랜치가 안 보인다. 그 사실을 알리고 멈춘다.

**0-B. 모드 결정 + `$BASE` 감지**

```bash
gh pr view --json url,number,body,baseRefName 2>/dev/null
```

- 결과 있음 → **update 모드** (`3-B`)
- 결과 없음 → **create 모드** (`3-A`)

`$BASE` 결정:

- **create 모드** — `dev` (이 레포의 기본 브랜치이자 PR base).
- **update 모드** — 기존 PR 의 base: `BASE=$(gh pr view --json baseRefName --jq '.baseRefName')`
- `$ARGUMENTS` 에 사용자가 base 를 명시했으면 그 값 우선 (create 모드 한정).

**로컬 git 비교는 항상 `origin/$BASE` 기준이다.** `$BASE`(브랜치 이름)는 `gh pr create --base $BASE` 같은 GitHub 쪽 지정에만 쓰고, `git log`·`git diff` 등 로컬 비교는 전부 `origin/$BASE` 를 쓴다 — 로컬 `dev` 는 stale 일 수 있어 그걸 기준 삼으면 머지로 끌려온 남의 커밋이 diff 에 부풀려 섞인다.

**임시파일 경로 규칙 (동시 세션 격리)** — PR 본문 임시파일은 고정 경로가 아니라 **브랜치별 경로** `/tmp/pr_body_$SLUG.md` 를 쓴다 (`SLUG` = 브랜치명의 `/` 를 `_` 로 치환, 예: `chore/36-workflow-commands` → `/tmp/pr_body_chore_36-workflow-commands.md`). 두 워크트리 세션이 동시에 `/pr` 을 돌려도 본문 파일이 안 겹친다.

**Windows 에서 `/tmp` 를 넘길 때는 경로를 변환한다.** Git Bash 의 `/tmp` 는 실제로 Windows 임시 폴더(`cygpath -w /tmp` 로 확인)라 파일 자체는 잘 만들어진다. 문제는 그 **문자열을 Windows 도구에 그대로 넘길 때**다 — IntelliJ·Python·Write/Read 도구는 `/tmp/...` 를 드라이브 루트 `\tmp\...` 로 해석해 "파일 없음" 으로 실패한다.

- **셸(bash) 안에서만 쓸 때**: `/tmp/pr_body_$SLUG.md` 그대로.
- **Write·Read 도구로 본문을 저장·조회할 때**: `cygpath -w` 로 변환한 Windows 절대경로를 쓴다. 변환값을 `echo` 로 남겨 그 값을 도구 인자에 인라인으로 박는다.

  ```bash
  SLUG=$(git branch --show-current | tr '/' '_')
  cygpath -w "/tmp/pr_body_$SLUG.md" 2>/dev/null || echo "/tmp/pr_body_$SLUG.md"
  ```

  (`cygpath` 가 없는 환경 = Windows 가 아니므로 원래 경로가 그대로 맞다.)

**셸 변수는 bash 호출 간 유지되지 않는다.** 본문 파일을 다루는 각 블록은 `SLUG=$(git branch --show-current | tr '/' '_')` 를 자기 안에서 다시 구한다. 같은 이유로 `$BASE`·`$ISSUE_LABELS` 처럼 앞 블록에서 결정된 값도 뒤 블록에서 변수 참조로 기대지 않고, 결정 시점에 `echo` 로 남긴 실제 값을 인라인으로 박는다.

### 1단계: 정보 수집

아래를 병렬로 실행해 변경 내역을 파악한다:

- `git log origin/$BASE..HEAD --oneline` — 커밋 목록
- `git diff origin/$BASE...HEAD --stat` — 변경 파일 요약
- `git diff origin/$BASE...HEAD` — 실제 변경 내용
- `git status` — 커밋되지 않은 변경이 있는지

커밋되지 않은 변경이 있으면 먼저 커밋할지 사용자에게 확인한다.

**브랜치 이름에서 이슈 번호 자동 추출**: 정규식 `^[a-z]+/(\d+)-` (예: `chore/36-workflow-commands` → `#36`).

- 매칭 → `## 연관 이슈\n- close #{번호}` 를 본문에 자동 채움
- 매칭 안 됨 → 섹션은 유지하되 항목을 `- ` 빈 줄로 둔다. 이슈 미연결 상태가 본문에 드러나야 작성자가 의도적으로 비웠음을 인지한다.

**PR 라벨 결정 — 연관 이슈 라벨 우선, 없으면 브랜치 prefix**:

```bash
# 한 블록에서 끝까지 계산하고 echo 로 값을 남긴다 — 셸 변수는 블록 간 유지되지 않는다.
# 분류 라벨 8종 (정의는 /issue 의 B-2). 이 목록에 없는 라벨은 분류가 아니다.
CLASSIFICATION='feat fix refactor perf chore docs test infra'

PICKED=""
# 1순위: 연관 이슈의 라벨 중 분류 라벨
for l in $(gh issue view {번호} --json labels --jq '.labels[].name' 2>/dev/null); do
  case " $CLASSIFICATION " in *" $l "*) PICKED=$l; break ;; esac
done
# 2순위: 브랜치 prefix (오타·비표준 prefix 는 위 목록에 없으므로 자동으로 걸러진다)
if [ -z "$PICKED" ]; then
  PREFIX=$(git branch --show-current | cut -d/ -f1)
  case " $CLASSIFICATION " in *" $PREFIX "*) PICKED=$PREFIX ;; esac
fi
ISSUE_LABELS=$PICKED
echo "ISSUE_LABELS=${ISSUE_LABELS:-없음}"
```

라벨이 있으면 `3-A`(LABEL_ARGS) / `3-B`(EDIT_ARGS) 에서 부여한다. 둘 다 비면 그때만 라벨 없이 진행한다.

> **분류 라벨만 승계하는 이유.** PR 은 연관 이슈와 같은 분류를 갖는 것이 자연스러우므로 이슈 라벨을 우선한다. 다만 이슈에 붙은 라벨이 전부 *분류* 는 아니다 — 커맨드 도입 이전 이슈들이 달고 있는 `task`·`epic`, GitHub 기본 라벨(`bug`·`enhancement` 등)은 분류 축이 아니다. 이걸 그대로 승계하면 `feat` 브랜치의 PR 이 `task` 라벨을 달아 정보량이 오히려 준다(실제로 PR #35 에서 그렇게 됐다). 그래서 **8종에 속하는 라벨만** 승계하고, 없으면 브랜치 prefix 로 떨어진다. 이 fallback 이 없으면 이슈 없이 만든 브랜치의 PR 이 조용히 라벨 없이 올라간다.

### 2단계: STAR 본문 작성 — create 모드 한정

(update 모드는 `3-B` 의 자체 가이드를 따른다)

```
## Situation
- 이 작업이 필요했던 배경·문제 상황
- 대화에서 논의된 동기나 맥락

## Task
- 해결하려 한 핵심 과제
- 대화에서 중요하게 다뤘던 고민이나 결정 포인트

## Action
- 실제로 한 일 (코드 변경 기반)
- 대화에서 논의한 트레이드오프나 선택의 이유

## Result
- 변경의 결과·효과 (CI 가 보증하는 "테스트 통과" 류 자명한 사실은 적지 않음)
- 주의할 점이나 후속 작업이 있으면 언급

---
## 연관 이슈

- close #{자동 추출된 번호}     ← 추출 실패 시 `- ` 빈 항목으로
```

**작성 지침:**

- 각 섹션은 bullet point(`-`)로 작성
- 대화에서 나온 고민, 왜 이 방식을 선택했는지, 어떤 대안을 검토했는지를 우선 반영
- diff 에서만 보이는 기계적 변경 나열은 최소화
- **사람 말로 먼저, 코드 이름은 최소.** 클래스·메서드·상수명을 모든 문장에 박지 않는다. 본문은 일상어로 "무엇을·왜" 를 말하고, 꼭 필요한 식별자는 Action 의 "구현" 묶음 한 곳에 모은다. 기준: 이 도메인 처음 보는 팀원이 표·첫 문장만으로 "누가 무엇을" 을 이해하는가.
- **대조·비교 정보는 표로.** 역할·분기·매핑, 응답 계약(케이스별 status·code·detail), 옵션 비교처럼 **대상·조건이 갈리는 정보**는 산문보다 표가 빠르게 읽힌다. 행=대상/케이스, 열=비교 축.
- **결정 트레이드오프는 비교 표로.** 대안을 저울질한 결정(A 안 vs B 안)은 선택지를 행, 장단점·비용을 열로. 검토 후 채택 안 한 안도 같은 표에 둔다.
- **동작 규칙은 굵은 키워드 불릿으로.** 산문 한 문단에 규칙 여러 개를 잇지 않는다.
- **수학기호·과한 약어 금지.** `∪`·`∩` 같은 기호는 "그리고/합쳐서/둘 다" 로, 약어는 풀어서.
- **한 문장 한 뜻.** 절을 여러 개 길게 잇지 말고 끊는다.
- **섹션 간 재진술 금지.** 특히 Result 는 Situation·Task 에 이미 쓴 문제를 "~문제가 해소됐다" 로 다시 풀어 쓰지 않는다. Result 에는 새 정보만 — 결과 수치·효과·리스크·후속.
- 한국어로 작성, 기술 용어는 영어 허용
- **제목·본문에 이모지·물결(`~`)·em dash 를 쓰지 않는다.** `~text~` 는 GitHub-flavored markdown 이 취소선으로 렌더링해 두 물결 사이를 통째로 줄 그어버린다. 대체: 곁가지는 쉼표·괄호·콜론이나 문장 분리로, approximately 는 "약", 범위는 "에서" 나 하이픈으로.
- **1단계에서 수집한 `git log` 의 모든 커밋이 STAR(특히 Action)에 빠짐없이 반영됐는지 최종 점검한다.** 기억·추측이 아니라 로그와 대조한다.
- **CI 가 보증하는 자명한 결과는 적지 않는다.** "전체 테스트 통과"·"컴파일 성공" 같은 머지 전제 사실은 리뷰어에게 새 정보가 0 이다. 검증은 **"무엇을·어떻게·왜 그렇게 확인했나" 가 비자명할 때만** 적는다 — 동시성·negative control·실측으로 확정한 가정·분기 망라의 폭·검증의 한계 같은 것.

**Action 섹션 그룹화:**

- bullet 이 6개 이상이거나 결이 다른 갈래(설계 / 안전망 / 검토 후 채택 안 한 안 등)가 섞이면 sub-heading 으로 그룹화한다.
- 그룹명은 이번 PR 이 다룬 결을 가장 잘 드러내는 이름으로. 매번 같을 필요 없음.
- bullet 5개 이하의 짧은 PR 은 그룹화 없이 평범한 리스트로.

**채울 수 없는 섹션:** 대화 컨텍스트나 diff 에서 근거를 찾을 수 없으면 **억지로 채우지 않는다.** `- TODO: 작성자가 직접 보완해주세요` 를 남긴다.

### 본문 확인 — IDE 로 열기 (create · update 공통)

본문 초안을 채팅에 길게 펼치지 않고, 저장한 본문 파일을 IntelliJ 로 열어 사용자가 **직접 보고 편집**하게 한다. 임시 파일이 single source 이므로 사용자가 IDE 에서 고친 내용이 그대로 `--body-file` 로 올라간다.

```bash
SLUG=$(git branch --show-current | tr '/' '_')
BODY=/tmp/pr_body_$SLUG.md

# IDE 열기 — macOS 를 1순위로, 안 되면 Windows 로 내려간다.
# 순서를 뒤집지 않는다: Windows 분기는 powershell.exe 호출이라 macOS 에서는 무의미하고,
# 먼저 시도하면 매번 실패 비용만 든다.
open_in_ide() {
  file=$1
  [ -n "${CI:-}" ] && return 1                      # CI 등 GUI 없는 실행은 바로 폴백

  # 1순위) macOS·Linux — PATH 의 idea 를 그대로 쓴다 (JetBrains Toolbox 가 심링크를 깔아둔다)
  command -v idea >/dev/null 2>&1 && { idea "$file"; return 0; }

  # 2) Windows: Git Bash PATH 에는 idea 가 없어도 PowerShell 은 찾는다 (App Paths·Windows PATH).
  #    - 경로는 cygpath -w 로 변환한다. Git Bash 의 /tmp 는 실제로 Windows 임시 폴더지만,
  #      "/tmp/..." 문자열을 그대로 넘기면 Windows 도구가 드라이브 루트 \tmp 로 해석해 못 찾는다.
  #    - Start-Process 로 띄운다. 런처를 직접 부르면 포그라운드로 붙어 셸이 멈춘다(실측: 2분 타임아웃).
  #    - 같은 폴더의 idea64.exe 를 우선한다. idea.bat 을 띄우면 콘솔 창이 함께 떠서
  #      IntelliJ 기동 로그가 사용자 화면에 그대로 쏟아진다.
  if command -v powershell.exe >/dev/null 2>&1; then
    win=$(cygpath -w "$file" 2>/dev/null || printf '%s' "$file")
    powershell.exe -NoProfile -Command "
      \$c = Get-Command idea -ErrorAction SilentlyContinue
      if (-not \$c) { exit 1 }
      \$exe = Join-Path (Split-Path \$c.Source) 'idea64.exe'
      if (-not (Test-Path \$exe)) { \$exe = \$c.Source }
      Start-Process -FilePath \$exe -ArgumentList '$win' -WindowStyle Hidden
      exit 0" >/dev/null 2>&1 && return 0
  fi
  return 1
}

if open_in_ide "$BODY"; then
  echo "IntelliJ 로 열었습니다: $BODY"
else
  echo "IDE 를 못 열었습니다. 파일 경로: $BODY"   # 본문을 채팅에 통째로 쏟지 않는다
fi
```

- 연 뒤 **확인 게이트를 `AskUserQuestion` 번호 선택으로 띄운다.** 자유 텍스트로 묻지 않는다. 채팅엔 제목 한 줄만 짧게 남긴다. **첫 옵션의 동사는 0-B 에서 결정된 모드를 그대로 따른다** — create 면 "올린다", update 면 "갱신한다". 슬래시로 둘 다 띄우지 않는다:
  - **이대로 올린다** (update 모드면 "이대로 갱신한다")
  - **취소**
- 본문을 고치고 싶으면 `AskUserQuestion` 의 **Other 에 "이렇게 고쳐줘" 를 직접 입력**한다. 고친 뒤 이 게이트를 다시 띄운다. "수정해줘" 를 별도 옵션으로 두지 않는 이유: 옵션은 눌러도 수정 내용이 안 담겨 한 번 더 물어야 하고, 그러면 취소와 다를 바 없다.
- **확인 이후 그 파일을 다시 Write 로 덮어쓰지 않는다.** 사용자가 IDE 에서 편집했을 수 있다. 최종 본문을 알아야 하면 Read 로 다시 읽는다.

### 3-A. Create 모드 — PR 신규 생성

1. 원격에 푸시되지 않았으면 `git push -u origin {브랜치명}`
2. PR 제목과 본문 초안을 작성해 **`/tmp/pr_body_$SLUG.md` 에 저장(Write)** 한다. 이어서 본문 파일을 IDE 로 열어 확인받는다. 제목은 채팅에 한 줄로 함께 보인다.
3. 확인 후 PR 생성 — assignee·라벨을 함께 부여:

   ```bash
   SLUG=$(git branch --show-current | tr '/' '_')
   ISSUE_LABELS="{1단계 echo 로 확인한 값. '없음'이면 빈 값}"
   # 라벨 플래그는 배열로 — `${VAR:+--label "$VAR"}` 관용구는 zsh 가 unquoted 확장을 word split 하지
   # 않아 "--label chore" 한 단어가 되어 unknown flag 로 터진다 (bash/zsh 양쪽 안전형).
   LABEL_ARGS=()
   [ -n "$ISSUE_LABELS" ] && LABEL_ARGS=(--label "$ISSUE_LABELS")
   gh pr create --base dev \
     --title "{제목}" \
     --body-file /tmp/pr_body_$SLUG.md \
     --assignee @me \
     "${LABEL_ARGS[@]}"
   ```

   - 라벨이 레포에 없어 실패하면 라벨 없이 재시도하고 사용자에게 보고한다.
4. PR URL 과 부여된 assignee·라벨을 사용자에게 전달한다.

### 3-B. Update 모드 — 기존 PR 본문 갱신

1. 기존 본문 가져오기:

   ```bash
   SLUG=$(git branch --show-current | tr '/' '_')
   gh pr view --json body --jq '.body' > /tmp/pr_body_$SLUG.md
   ```

2. **이번 추가 변경 내역을 `git log` 로 정확히 식별한다 — 기억·추측에 의존하지 않는다.**

   ```bash
   git log origin/dev..HEAD --oneline   # PR 의 전체 커밋 (merge 있으면 --no-merges)
   ```

   전체 커밋과 실제 변경을 기존 본문과 대조해 **본문이 낡은 자리**(이번 변경으로 거짓이 된 서술, 아직 반영 안 된 작업)를 가려낸다.

3. **본문 STAR 를 최종 상태로 직접 고친다 (살아있는 본문).** 기준 한 줄: **"머지 후 이 본문만 읽은 독자가 최종 상태를 정확히 이해하는가."**

   - **`## Updates` 같은 증분 append 섹션을 두지 않는다.** 증분 항목은 이미 최종으로 고친 본문·커밋 메시지와 삼중 중복이라 본문만 어지럽힌다. **증분 원장은 PR 커밋 탭과 본문 edit history 가 담당**한다 — 스쿼시 머지 후에도 PR 페이지에 둘 다 남는다.
   - 낡은 서술이 있는 자리만 **표적 수정**한다 — 본문 전체를 재생성하지 않는다.
   - 큰 전환(접근 전환·스코프 변경)은 "처음 시도 → 전환한 흐름" 자체를 STAR 서사에 흡수한다 (`/commit` 본문 철학과 같은 결).
   - **남이 쓴 본문은 건드리지 않는다.** 사람이 GitHub 에서 직접 고친 문구는 보존하고, 봇 소유 블록(auto-generated 마커 쌍 등)은 마커째 유지한다 — `gh pr edit --body-file` 은 전체 덮어쓰기라 한 번 누락되면 복구되지 않는다.
   - 리뷰어에게 "지난 리뷰 이후 무엇이 바뀌었나" 를 알릴 필요가 있으면 본문이 아니라 **PR 코멘트**로 남긴다.

4. **제목 변경 필요 검토**: 추가 변경으로 작업 의도·스코프가 바뀌었거나 오타·부정확한 표현이 있으면 새 제목 제안. 그 외엔 유지.
5. 갱신본을 `/tmp/pr_body_$SLUG.md` 에 저장(Write)한 뒤 IDE 로 열어 확인받는다.
6. 확인 후 `gh pr edit --body-file /tmp/pr_body_$SLUG.md` 로 갱신 (별도 bash 호출이라 `SLUG` 를 다시 구한다). 제목 변경이 있으면 `--title "새 제목"` 추가.
7. **CodeRabbit 리뷰 대응** — 이번 변경이 리뷰 대응이라면 commit + push 로 끝내지 않는다. 리뷰 조회·평가·reply·resolve 는 **`/coderabbit`** 이 담당한다. 사람 리뷰 thread 는 작성자가 직접 답하므로 `/coderabbit` 도 건드리지 않는다.
8. **메타데이터 보정** — 이전에 만든 PR 은 assignee·라벨이 비어 있을 수 있다. 멱등하게 보정한다:

   ```bash
   ISSUE_LABELS="{1단계 echo 로 확인한 값. '없음'이면 빈 값}"
   EDIT_ARGS=(--add-assignee @me)
   [ -n "$ISSUE_LABELS" ] && EDIT_ARGS+=(--add-label "$ISSUE_LABELS")
   gh pr edit "${EDIT_ARGS[@]}"
   ```

9. PR URL 을 재출력한다.

### PR 제목 규칙

- 70자 이내
- 타입 prefix 를 붙이지 않는다 (커밋과 다름)
- 예: `예외 처리 시스템 공통화`, `개발 워크플로우 슬래시 커맨드 이식`
- update 모드에서는 기본적으로 제목 유지. 의도·스코프 변화나 정정이 필요한 경우만 변경하고 사용자에게 이유를 짚어 확인받는다.

## 주의 사항

- **owner/repo 는 하드코딩하지 않는다** — 모든 `gh` 명령이 현재 워크트리의 origin 에서 레포를 자동 도출한다.
- **Project 보드 연동은 이 레포에 없다.** piki/core 이식 시 관련 단계(`gh project item-add`, Status·Start date mutation)를 걷어냈다. 보드를 도입하면 그때 다시 넣는다.

$ARGUMENTS

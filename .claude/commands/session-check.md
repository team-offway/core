세션을 종료해도 되는지 **read-only 로 점검**한다 — 기본은 현재 워크트리·브랜치만 훑고, 인자 `all` 을 주면 전체 워크트리·브랜치·PR 을 함께 스윕한다. **점검 단계는 어떤 변경도 하지 않는다.** 커밋·푸시·브랜치 삭제·prune 은 직접 실행하지 않고 **안내만** 한다.

> **실행 환경**: 셸 블록은 **Bash 도구**로 실행한다.

## 모드

- **기본 (`/session-check`)** — 현재 워크트리 + 현재 브랜치·PR 만 본다. "지금 이 작업" 을 닫아도 되는지 확인하는 좁은 점검. `/session-close` 와 스코프가 일치한다.
- **전역 (`/session-check all`)** — 위에 더해 전체 워크트리 dirty·prunable, 모든 로컬 브랜치 미푸시·미삭제, 본인 열린 PR 전부를 스윕한다.

## 원칙

- **점검은 read-only.** 상태를 읽기만 한다. 정리가 필요하면 "무엇을 어떤 도구로 정리하라" 고 **안내만** 한다. **단 하나의 예외는 마무리(close)** 다 — 절차 6 참고.
- **`AskUserQuestion` 은 마지막 close 확인 한 곳에서만 띄운다.** 점검 항목의 정리는 전부 read-only 안내로 끝낸다.
- **정리 안내 vs 정보성.** 처리 방법이 있는 항목(uncommitted, 미푸시, 미삭제 브랜치, prunable 워크트리)은 도구를 함께 안내한다. 그 외(CI 실패, 리뷰 대기, 새 TODO, 다른 워크트리 dirty)는 정보성 리포트.
- **체크아웃 전환 금지.** 브랜치를 갈아타지 않는다 — 다른 워크트리는 `git -C <path>` 로 **읽기만** 한다.
- **파괴적 동작은 안내에서도 권하지 않는다.** untracked 삭제·`git checkout -- .`·stash drop·강제 푸시 등.
- **이모지·체크기호 금지.** 굵게·불릿으로만 표시한다.

## 점검 항목

먼저 현재 위치를 잡는다.

```bash
BR=$(git rev-parse --abbrev-ref HEAD)
CUR=$(git rev-parse --show-toplevel)
echo "BR=$BR CUR=$CUR"
```

### A. 현재 워크트리 Git 상태 (공통)

```bash
git status --porcelain
git stash list
```

- uncommitted(staged + unstaged) → **정리 안내**: `/commit`, 또는 stash·그대로 둠.
- untracked → 그중 `.md`·스크래치 파일은 작업 부산물일 수 있으니 따로 표시한다 (삭제는 안 함, 커밋 여부는 `/commit` 판단에 위임).
- stash 잔여 → **정보성** 리포트만.

### B. 현재 브랜치 · PR (공통)

```bash
git for-each-ref --format='%(refname:short) | %(upstream:short) | %(upstream:track)' refs/heads/"$BR"
gh pr list --author "@me" --state open --head "$BR" --json number,title,headRefName,reviewDecision,statusCheckRollup
```

- `%(upstream:track)` 가 `[gone]` → upstream 이 삭제됨 = **머지된 신호**. 단 현재 브랜치는 현재 워크트리에 체크아웃돼 있어 `git branch -D` 로 삭제 불가다. → **안내**: "이 브랜치는 머지됨 → 워크트리째 정리하려면 `/session-close`."
- upstream 이 없음(한 번도 push 안 됨)이거나 ahead → **정리 안내**: `git push`(필요 시 `-u`). `[gone]` 과 혼동하지 않는다 — `[gone]` 은 push 가 아니라 머지 신호다.
- PR 의 CI 실패(`statusCheckRollup` 에 FAILURE)·리뷰 대기(`reviewDecision` 가 REVIEW_REQUIRED·CHANGES_REQUESTED) → **정보성** 리포트.

### C. 전체 워크트리·브랜치 스윕 (`all` 한정)

기본 모드에선 **건너뛴다.**

```bash
git worktree list --porcelain
git worktree prune --dry-run -v
git for-each-ref --format='%(refname:short) | %(upstream:short) | %(upstream:track)' refs/heads
gh pr list --author "@me" --state open --json number,title,headRefName,reviewDecision,statusCheckRollup
```

파싱한 각 워크트리 경로에 대해 `git -C "<worktree_path>" status --porcelain`.

- 다른 워크트리에 dirty·untracked → **정보성** 리포트 (거기서 정리하라고 안내).
- prunable 워크트리 → **정리 안내**: `git worktree prune`.
- 현재 브랜치가 아닌 다른 브랜치가 `[gone]` → **정리 안내**: `git branch -D <branch>`.
  - **`-D` 를 권한다.** squash·rebase 머지된 브랜치는 커밋이 현재 HEAD 의 ancestor 가 아니라 `git branch -d`(안전 삭제)가 "not fully merged" 로 거부한다. `[gone]` 은 원격에서 이미 사라진 머지 완료 브랜치라 force 삭제가 안전하다. 확신이 필요하면 `gh pr list --state merged --json headRefName` 로 교차 확인하라고 함께 안내한다.
  - 단 그 브랜치가 어느 워크트리에 체크아웃돼 있으면 `git branch -D` 가 거부된다 → "그 워크트리부터 `/session-close` 로 정리" 안내.
- 다른 브랜치의 upstream 없음·ahead → **정리 안내**: `git push`, 다른 워크트리에 물린 브랜치는 `git -C <path> push`.

### D. 작업 흔적 (공통, 정보성)

```bash
# base 는 origin/dev (이 레포의 PR base). origin/main 이 아니다.
# .claude/** 는 제외 — 커맨드 문서의 "TODO" 단어가 오탐되므로.
git diff origin/dev...HEAD -- ':(exclude).claude/**' | grep -nE '^\+[^+].*(TODO|FIXME)'
```

- 이번 브랜치에서 새로 추가된 TODO·FIXME → 리포트만.
- 현재 브랜치 PR 에 미해결 CodeRabbit thread 가 있으면 건수만 알리고 `/coderabbit` 으로 처리하라고 안내한다 (여기서 처리하지 않는다).

## 최종 리포트 형식

```
## 세션 마무리 점검 (<기본 | 전역(all)>)

브랜치: <현재 브랜치>

**정리 필요 (주의 <M>건)** — 각 줄 끝의 도구로 사용자가 직접 정리
- (현재) uncommitted <n>건 (staged <a> / unstaged <b> / untracked <c>) → /commit
- 미푸시: <branch> ahead <n> → git push
- 머지됨(현재 브랜치): <branch> (upstream [gone]) → /session-close
- CI 실패: PR #<num> (정보성)
- [all] (워크트리 <name>) dirty <n>건 (정보성 — 거기서 정리)
- [all] 미삭제(머지됨): 로컬 브랜치 <branch> (upstream [gone]) → git branch -D

**안전**
- stash 없음 / 새 TODO 없음
```

주의 0건이면 맨 위에 **"닫아도 안전합니다"** 한 줄을 명확히 띄운다.

## 절차

1. 인자에 `all` 이 포함됐는지로 모드를 정한다 (없으면 기본).
2. read-only 로 상태를 수집한다 — 기본은 A·B·D, 전역은 A·B·C·D.
3. 최종 리포트 형식으로 현재 상태를 보여준다.
4. 정리가 필요한 항목은 "무엇을 어떤 도구로 정리할지" 함께 적는다. **이 항목들은 실행하거나 `AskUserQuestion` 을 띄우지 않는다.**
5. 한 줄 결론(닫아도 안전 / 남은 주의 N건)으로 마무리한다.
6. **close 는 `AskUserQuestion` 으로 묻고 '예' 일 때만 호출한다.** 결론이 "닫아도 안전" 이고, 현재 워크트리가 메인 체크아웃이 아니면서 머지+clean 인 **제거 대상 sub-worktree** 일 때:
   - 최종 리포트를 보여준 **뒤** "이 워크트리를 정리하고 마무리하려면 `/session-close` 를 호출할까요?" 를 묻는다. 옵션은 **호출(첫 번째·추천) / 호출 안 함**.
   - **호출**을 고르면 그때 `/session-close` 를 호출한다. **호출 안 함**이면 아무것도 실행하지 않고 안내만 한다.
   - **사용자가 명시적으로 고르기 전에는 절대 먼저 실행하지 않는다.**
   - 현재가 메인 체크아웃이거나 제거 대상 워크트리가 없으면 이 ask 를 생략한다.
   - 결론이 "닫아도 안전" 이 아니면 ask 하지 않는다 — 먼저 정리가 우선이다.

owner/repo 는 하드코딩하지 않는다 — 모든 `gh` 명령이 현재 워크트리의 origin 에서 레포를 자동 도출한다.

$ARGUMENTS

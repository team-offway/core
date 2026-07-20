이슈를 생성하고, 브랜치(또는 워크트리)를 만들고, 이슈에 매핑하고, 라벨을 설정합니다.

## 원칙

**이슈는 "왜 / 무엇을" 만 받는다.** 우선순위·시작일·마감일 같은 메타 정보는 묻지 않는다. 작업 시작 시점에 적는 메타는 거의 추정치라 데이터 품질이 낮다.

**이슈는 작업의 시작점이라 사용자 입력이 source of truth.** 모델이 추측해 본문을 채우거나 분류를 단정하지 않는다. 자동화하는 부분은 모두 검수 단계에서 사용자 OK 를 받는다.

**Epic 과 일반 이슈는 본질이 다르다.** Epic 은 여러 작업의 묶음으로 자체 코드 작업이 없어 브랜치를 만들지 않는다. 일반 이슈는 코드 작업의 단위로 부모 Epic 에 묶일 수 있다. 이 구분만 사용자가 명시하고, 그 외 분류·prefix·상위 Epic 추천은 모델이 본문을 보고 자동 결정한 뒤 검수받는다.

**작업 공간은 워크트리를 기본으로 한다.** 일반 이슈의 브랜치를 만들 때 현재 체크아웃을 그 브랜치로 끌고 가는 대신 `.claude/worktrees/` 에 격리된 워크트리로 분리하는 것을 추천(첫 번째 옵션)한다. 메인 체크아웃이 작업 중 외부에서 바뀌어도 격리된다. 사용자가 현재 브랜치 작업을 고를 수 있게 묻되 default 는 워크트리. Epic 은 브랜치 자체가 없어 해당 없음.

**라벨은 한 작업 한 개.** type 차원(`feat`/`fix`/`refactor`/`perf`/`chore`)과 영역 차원(`docs`/`test`/`infra`)이 한 라벨로 합쳐져 있다. "외부 가시적 변화" 가 본질이면 그 type, 특정 영역만 만지면 그 영역. multi-label 부여하지 않는다.

**채팅 끊김을 최소화한다.** 자유 텍스트는 한 메시지로 일괄 받고, 옵션 결정은 `AskUserQuestion` 으로 묶어 받는다. 사용자 인터랙션은 보통 **3~4 라운드**(Epic 여부 → 자유 입력 → 검수 → [일반 이슈] 작업 위치) 안에 끝난다.

## 절차

### 1단계: Epic 여부

`AskUserQuestion` (single-select):

- `일반 이슈 (Recommended)`
- `Epic`

대화 컨텍스트가 명확하면 default 로 표시. 모호하면 추측 없이 그대로 보여준다.

---

## A. Epic 흐름

Epic 은 브랜치를 만들지 않고 상위 Epic 도 없다.

### A-1. 자유 입력 (1회)

사용자에게 다음과 같이 안내:

> "이슈 내용을 자유롭게 적어주세요. 제목 / 왜 / 무엇을 모두 포함해 한 번에 작성하시면 됩니다. 모델이 알아서 분해·정리합니다."

사용자 입력이 너무 짧아 분해가 어려우면 1~2 번 follow-up 질문. 더 이상 묻지 않는다.

### A-2. 모델 자동 분해 + 중복 검사

자유 입력을 분석해 결정:

- **제목** (한 줄, 70자 이내, 슬러그 의미 보존)
- **왜** (배경, 필요성)
- **무엇을** (목표 상태, 예상 범위)

분해 룰:
- 표현 자연스럽게, 단답을 풍부한 문장으로 풀기 OK
- 적절한 컨텍스트 추론 OK (예: "성능 개선" → "현재 X 처리가 느려 개선 필요")
- 구체적 수치·날짜·외부 시스템·라이브러리·책임자를 **새로 만들어내기 금지**
- 사용자가 명시한 사실과 다른 내용 금지

분해된 제목으로 중복 이슈 사전 검사:

```bash
gh issue list --search "{제목 키워드}" --state open --json number,title,labels --limit 5
```

결과가 1개 이상이면 검수 단계에서 함께 보여준다.

### A-3. 검수 — `AskUserQuestion`

분해된 본문 + 중복 검사 결과를 한 화면에 보여주고:

```
[본문 미리보기]
## 왜
{분해된 내용}

## 무엇을
{분해된 내용}

[중복 검사 결과]
유사 이슈: #X "..." (있을 때만)
```

`AskUserQuestion` (single-select):

- `OK 진행 (Recommended)`
- `본문 수정 필요` — Other 로 어떤 부분을 어떻게
- `중복 — 새 이슈 만들지 않음` (중복 검사 결과 있을 때만)

수정 요청이 들어오면 반영하고 다시 검수. 만족할 때까지 반복.

### A-4. 이슈 생성

본문은 **반드시 heredoc 으로 stdin 에 넘긴다**(`--body-file -`). 제목·본문은 사용자가 쓴 자유 텍스트라 백틱·`$(...)`·따옴표·줄바꿈이 들어올 수 있고, 명령줄에 그대로 박으면 셸이 그걸 해석·실행한다.

```bash
# 제목은 변수에 담아 인용부호로 감싼다 (명령 치환을 막으려면 single quote).
title='{제목}'

# 본문은 quoted heredoc('EOF')로 넘긴다 — quote 를 붙여야 $·백틱이 확장되지 않는다.
gh issue create \
  --title "$title" \
  --label "epic" \
  --assignee @me \
  --body-file - <<'EOF'
{본문}
EOF
```

### A-5. 결과 출력

- 이슈 URL
- "Epic 은 브랜치를 만들지 않습니다. 하위 작업은 `/issue` 로 일반 이슈를 만들고 그 단계에서 이 epic 이 자동 추천됩니다."

---

## B. 일반 이슈 흐름

### B-1. 자유 입력 (1회)

A-1 과 동일.

### B-2. 모델 자동 분해 + 자동 결정 + 중복 검사

**자유 입력 분해** — A-2 와 동일 룰.

**분류 자동 결정** (single-select): `feat` / `fix` / `refactor` / `perf` / `chore` / `docs` / `test` / `infra`

우선순위: **외부 가시적 변화** > **영역 한정**.

1. 외부 가시적 변화 시그널이 있으면:
   - "버그", "안 됨", "결함 수정", "예외 발생" → `fix`
   - "성능 개선", "속도", "메모리 절감", "latency" → `perf`
   - "새 API", "새 엔드포인트", "외부 사용자·클라이언트 노출 새 기능" → `feat`
   - "구조 개선", "리팩터링", "정리", "추출" (외부 동작 불변) → `refactor`

2. 외부 동작 변화 없고 특정 영역만 만지면:
   - 문서만 (CLAUDE.md, README, `.claude/rules/` 등) → `docs`
   - 테스트만 → `test`
   - 인프라 (배포, 시크릿, 워크플로우) → `infra`
   - 그 외 (빌드·deps·CI·도구·리포 설정·잡일) → `chore`

3. 모호하면 `chore` fallback.

**브랜치 prefix**: 라벨 문자열을 그대로 사용한다 (예: `feat`, `chore`). 슬래시는 브랜치명 템플릿(`{prefix}/{이슈번호}-{slug}`)에서만 추가되므로 prefix 자체에 슬래시를 포함하지 않는다.

**상위 Epic 추천**:

```bash
gh issue list --label epic --state open --json number,title --limit 20
```

활성 epic 목록과 본문(제목+왜+무엇을)의 의미 비교로 가장 관련 있는 1개 추천. 없으면 추천 안 함.

**중복 이슈 사전 검사**: A-2 와 동일.

### B-3. 검수 — `AskUserQuestion`

본문 + 자동 결정 + 중복 검사 결과를 한 화면에 보여주고:

```
[본문 미리보기]
## 상위 Epic
#{추천 번호}            ← 없으면 통째 생략

## 왜 / 무엇을
...

[자동 결정]
- 분류 라벨: {라벨}
- 브랜치 prefix: {라벨 그대로, 슬래시 없이}
- 상위 Epic: #{번호} 또는 없음

[중복 검사 결과]
유사 이슈: #X "..." (있을 때만)
```

`AskUserQuestion` (single-select):

- `OK 진행 (Recommended)`
- `본문 수정 필요` — Other 로 어떤 부분을 어떻게
- `라벨 수정` — Other 로 어떻게 (라벨이 곧 브랜치 prefix 라 함께 바뀜)
- `중복 — 새 이슈 만들지 않음` (중복 검사 결과 있을 때만, 다른 옵션 1개와 묶어 4개 한도)

수정 반영 후 만족할 때까지 반복.

### B-4. 이슈 생성

A-4 와 같은 이유로 본문은 quoted heredoc 으로 넘긴다.

```bash
title='{제목}'

gh issue create \
  --title "$title" \
  --label "{선택된 분류 라벨}" \
  --assignee @me \
  --body-file - <<'EOF'
{본문}
EOF
```

### B-5. 작업 위치 선택 + 브랜치·워크트리 생성

브랜치명 슬러그는 분해된 제목에서 영문 kebab-case 로 생성 (한국어면 의미 보존하며 영문 의역). 브랜치명은 `{prefix}/{이슈번호}-{slug}`, 워크트리 디렉터리명은 `{slug}` (prefix·번호 없이).

`AskUserQuestion` (single-select) 으로 묻는다. **워크트리를 추천(첫 번째)으로 둔다**:

- `워크트리로 분리 (Recommended)` — 현재 체크아웃은 그대로 두고 `.claude/worktrees/{slug}` 에 격리된 작업 공간을 만든다.
- `현재 브랜치에서 작업` — 현재 디렉터리를 새 브랜치로 전환한다.

#### 워크트리 선택 시

1. GitHub 브랜치 생성 + 이슈 연결. **`--checkout` 을 주지 않는다** — 현재 디렉터리를 끌고 가면 같은 브랜치를 워크트리에서 다시 체크아웃할 수 없어 충돌한다.

   ```bash
   gh issue develop {이슈번호} \
     --base dev \
     --name "{prefix}/{이슈번호}-{slug}"
   ```

2. `gh` 가 만든 원격 브랜치를 가져와 워크트리로 분리한다:

   ```bash
   git fetch origin "{prefix}/{이슈번호}-{slug}":"refs/remotes/origin/{prefix}/{이슈번호}-{slug}"
   git worktree add ".claude/worktrees/{slug}" "{prefix}/{이슈번호}-{slug}"
   ```

   `git worktree add <path> <branch>` 는 로컬에 `{branch}` 가 없고 `origin/{branch}` 가 정확히 하나면 DWIM 으로 추적 브랜치를 만들어 붙인다. DWIM 이 안 되면 명시형 fallback: `git worktree add --track -b "{prefix}/{이슈번호}-{slug}" ".claude/worktrees/{slug}" "origin/{prefix}/{이슈번호}-{slug}"`.

3. 세션을 워크트리로 진입시킨다 — `EnterWorktree` 도구를 **`path=".claude/worktrees/{slug}"`** 로 호출 (이미 만든 워크트리에 진입). `name=` 으로 새로 만들지 않는다 — 브랜치는 `gh issue develop` 이 base `dev` 로 이미 만들었기 때문. 이후 작업·커밋·`/pr` 은 이 워크트리에서 진행된다.

#### 현재 브랜치 선택 시

```bash
gh issue develop {이슈번호} \
  --base dev \
  --name "{prefix}/{이슈번호}-{slug}" \
  --checkout
```

생성 후 자동 checkout 이 안 되면 명시적으로 `git checkout {prefix}/{이슈번호}-{slug}` 실행.

### B-6. 결과 출력

- 이슈 URL
- 브랜치명
- **워크트리 선택 시**: 워크트리 경로 + "세션이 워크트리로 전환됐고 현재 체크아웃(`dev`)은 그대로 유지됩니다" + "작업·커밋·`/pr` 은 이 워크트리에서 진행됩니다"
- **현재 브랜치 선택 시**: 현재 체크아웃된 브랜치 확인
- 다음 단계 안내 (작업 시작 → `/commit` → `/pre-pr` → `/pr`)

---

## 주의 사항

- **owner/repo 는 하드코딩하지 않는다.** 모든 `gh` 명령이 `--repo` 를 생략해 현재 워크트리의 origin 에서 레포를 자동 도출한다.
- **base 브랜치는 `dev`.** 이 레포의 기본 브랜치이자 PR base 다. `main` 이 아니다.
- **자유 입력이 너무 짧으면 follow-up.** 다만 1~2 번 안에 끝낸다. 무한 follow-up 금지.
- **모델 분해 결과는 검수 필수.** 사용자가 거부하면 즉시 부분 수정 또는 원본 그대로.
- 분류 자동 결정은 시그널이 명확할 때만. 모호하면 `chore` fallback (보수적).
- 라벨이 레포에 없어 `gh issue create` 가 실패하면 에러 그대로 보고.
- `gh issue develop` 은 `--name` 을 명시한다 (인터랙티브 회피). `--branch-name` 은 존재하지 않는 옵션이니 주의. `--checkout` 은 **현재 브랜치 작업을 고른 경우에만** 붙인다.
- **워크트리 진입은 `EnterWorktree(path=...)` 로만.** `git worktree add` 로 먼저 만든 뒤 `path` 로 진입한다. `path` 로 진입한 워크트리는 `ExitWorktree` 가 제거하지 않으므로 정리는 `/session-close` 에 맡긴다.
- 본문에 `#{epic 번호}` 가 들어가면 GitHub 가 자동 cross-reference 링크 — 별도 sub-issue API 불필요.
- 중복 이슈 검사는 false positive 가능. 사용자가 "다른 이슈" 라 답하면 그대로 진행.
- **Assignee 는 `@me` 고정.** 이슈 만든 사람이 작업자라는 가정.
- **기존 `task` 라벨은 유지한다.** 이 커맨드 도입 이전에 만든 이슈들이 달고 있다. 새로 만드는 이슈만 8종 분류를 쓴다.
- **Project 보드·Issue Type 은 이 레포에 없다.** piki/core 에서 이식할 때 관련 단계(`gh project item-add`, `updateIssueIssueType` GraphQL)를 걷어냈다. 보드를 도입하면 그때 다시 넣는다.

$ARGUMENTS

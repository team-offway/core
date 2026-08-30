현재 브랜치 PR 의 CodeRabbit 리뷰를 처리한다 — 인라인 review thread 와 review body 의 nitpick 을 모두 조회해 평가하고, accept·reject 를 reply·resolve 로 남긴다. **reject 는 닫기 전에 사용자에게 선택을 받는다.**

> **실행 환경**: 셸 블록은 **Bash 도구**로 실행한다.

## 언제 쓰나

- PR 에 CodeRabbit 리뷰가 달린 뒤, 그 지적을 반영·답변할 때.
- `/pr` 로 PR 을 올린 뒤 리뷰 대응 단계에서 이어서 호출하거나, 단독으로 호출한다.

## 원칙

- **모든 reply 는 `@coderabbitai` 멘션으로 시작한다.** 예외 없다 — accept·reject·질문 전부. 절차 2 의 템플릿이 멘션을 붙여주므로 본문에는 내용만 적는다.
- **commit + push 로 끝내지 않는다.** 각 review thread 에 reply 를 남겨 **어떤 commit 으로 반영했는지 / reject 한 이유가 무엇인지** 가 conversation 에 박혀야 다른 리뷰어가 처리 여부를 헷갈리지 않는다.
- **CodeRabbit 은 코멘트를 두 곳에 나눠 단다 — 반드시 둘 다 본다.**
  - **인라인 review thread**: actionable 코멘트. `reviewThreads` 로 조회되고 개별 resolve 가능.
  - **review body 안에 접힌 nitpick·추가 코멘트**: `🧹 Nitpick comments (N)` 같은 `<details>` 블록. `reviewThreads` 에 **안 잡힌다** — `pulls/{PR}/reviews` 의 review body 를 따로 조회해야 보인다. 이걸 빠뜨리면 nitpick 을 통째로 놓친다 (resolve 대상은 아니지만 평가는 해야 한다).
- **author 매칭은 `coderabbitai` 로 시작하는지(`startswith`)로 한다.** GitHub 의 두 API 가 같은 봇을 다르게 표기한다 — GraphQL `reviewThreads` 의 `author.login` 은 `coderabbitai`, REST `pulls/{PR}/reviews` 의 `user.login` 은 `coderabbitai[bot]`. 어느 한쪽 값으로 고정하면 다른 API 에서 매칭이 깨져 봇을 사람으로 오인한다.
- **이 커맨드는 CodeRabbit thread·nitpick 만 다룬다 — 사람 리뷰는 조회·카운트·보고 어느 것도 하지 않는다.** 사람 리뷰 thread 는 작성자가 직접 의도·뉘앙스를 담아 답할 영역이다.
- **지적을 그대로 받아쓰지 않는다.** CodeRabbit 은 diff 조각만 보므로 같은 원인의 다른 발현을 놓치는 경우가 있다. 지적을 고치기 전에 **"이 원인으로 생기는 다른 케이스가 있나" 를 먼저 확인**한다 (실측 프로브가 가장 확실하다). 한 건만 고치고 넘어가면 같은 버그가 남는다.

## 절차

### 0. PR 번호 확인

```bash
PR=$(gh pr view --json number --jq '.number')
echo "PR #${PR}"
```

PR 이 없으면 먼저 `/pr` 로 PR 을 만들라고 안내하고 중단한다.

### 1. CodeRabbit 인라인 thread 조회

```bash
PR=$(gh pr view --json number --jq '.number')   # 블록마다 재도출 — 셸 변수는 bash 호출 간 유지되지 않는다
read -r OWNER NAME <<<"$(gh repo view --json owner,name --jq '.owner.login + " " + .name')"
# --paginate 가 커서 페이지네이션을 자동 처리한다 (변수명 $endCursor + pageInfo 선택이 규약).
# 수동 루프를 두지 않는 이유: hasNext 신호를 놓치면 100개 초과 thread 가 조용히 누락된다.
# comments(first: 20) — root 코멘트뿐 아니라 기존 reply 까지 가져온다.
# first:1 로 두면 (a) 무엇을 지적했는지 본문을 못 봐서 별도 조회를 또 해야 하고,
# (b) 이미 답한 thread 인지 몰라 중복 reply 를 달게 된다.
gh api graphql --paginate -f owner="$OWNER" -f name="$NAME" -f query='
  query($endCursor: String, $owner: String!, $name: String!) { repository(owner: $owner, name: $name) {
    pullRequest(number: '"$PR"') {
      reviewThreads(first: 100, after: $endCursor) {
        pageInfo { hasNextPage endCursor }
        nodes {
          id isResolved path line
          comments(first: 20) { nodes { author { login } body } }
        }
      }
    }
  }}' --jq '.data.repository.pullRequest.reviewThreads.nodes[]
            | select((.comments.nodes[0].author.login // "") | startswith("coderabbitai"))
            | {id, isResolved, path, line,
               replies: [.comments.nodes[1:][] | .author.login],
               body: .comments.nodes[0].body}'
```

**출력이 길면 파일로 받아 읽는다.** 지적 본문에는 CodeRabbit 의 분석 로그(`<details>` 안의 웹 검색·스크립트 실행 기록)가 붙어 실제 결론보다 훨씬 길다. 채팅에 그대로 쏟지 말고 파일에 저장한 뒤 결론부만 추린다.

**`replies` 가 비어 있지 않으면 이미 답한 thread 다.** 무엇을 답했는지 먼저 읽고, 중복 reply 를 달지 않는다.

### 1.5. review body 의 nitpick 조회 — reviewThreads 에 안 잡히므로 필수

```bash
PR=$(gh pr view --json number --jq '.number')
NWO=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
# --paginate 필수 — 기본 30건/페이지라, CodeRabbit 이 푸시마다 리뷰를 새로 다는 특성상
# 리뷰 왕복이 길어지면 첫 30개 이후의 review body(nitpick 포함)가 조용히 누락된다.
# 길이 필터를 두지 않는다 — 짧은 body 도 유효할 수 있어 비어있지 않으면 전부 수집하고,
# nitpick 여부는 길이가 아니라 본문 마커(`🧹 Nitpick comments` 등)로 가른다.
gh api --paginate "repos/$NWO/pulls/$PR/reviews" \
  --jq '.[] | select(((.user.login // "") | startswith("coderabbitai")) and ((.body // "") | length > 0)) | .body'
```

출력된 body 에서 `🧹 Nitpick comments` 등 접힌 코멘트를 건별 평가한다. nitpick 은 thread 가 아니라 **resolve 대상이 아니므로**, 반영하면 커밋하고 처리 사실을 **PR 코멘트**로 남긴다 (PR 본문이 아니다 — `/pr` 의 살아있는 본문 원칙 참고).

### 2. 각 CodeRabbit thread 에 reply

**아래 템플릿을 그대로 쓴다.** `MENTION` 이 본문 맨 앞에 자동으로 붙으므로, `body` 에는 **내용만** 적고 멘션을 직접 쓰지 않는다.

```bash
# 1) 내용만 담는다. single quote 필수 — accept reply 는 commit hash 를 backtick 으로
#    감싸는데(`371b5ba`), double quote 안에 직접 넣으면 셸이 명령 치환으로 실행해버린다.
body='<reply 내용 — 멘션은 여기 쓰지 않는다>'

# 2) 멘션은 템플릿이 붙인다. 이 두 줄을 지우거나 순서를 바꾸지 않는다.
MENTION='@coderabbitai'
reply="$MENTION"$'\n\n'"$body"

# 3) 전송
gh api graphql -f query='
  mutation($t: ID!, $b: String!) {
    addPullRequestReviewThreadReply(input: {pullRequestReviewThreadId: $t, body: $b}) {
      comment { url }
    }
  }' -F t="<thread id>" -f b="$reply"
```

**왜 구조로 강제하나** — 멘션이 없으면 CodeRabbit 이 그 reply 를 처리하지 않아 대댓글·재평가가 영영 오지 않는다 (accept 든 reject 든 무응답으로 끝난다). 이건 "잊지 말자" 로 될 일이 아니다. 실제로 이 커맨드를 쓰기 전 수동 대응에서 첫 reply 의 멘션이 빠졌고, 그걸 계기로 템플릿이 멘션을 붙이는 형태로 바꿨다.

**빠뜨렸다면 편집이 아니라 새 reply 를 단다.** 봇은 댓글 *생성* 이벤트에 반응하고 편집 이벤트는 무시하므로, 나중에 편집으로 멘션을 넣어도 소용없다.

**전송 후 확인한다** — 방금 만든 코멘트 본문이 멘션으로 시작하는지 본다. 아니면 위 규칙대로 새 reply 를 단다.

```bash
gh api graphql -f query='
  { node(id: "<thread id>") { ... on PullRequestReviewThread {
      comments(last: 1) { nodes { author { login } body } } } } }' \
  --jq '.data.node.comments.nodes[0].body | if startswith("@coderabbitai") then "OK 멘션 있음" else "누락 — 멘션 포함한 새 reply 필요" end'
```

### 3. 자동 resolve 안 된 thread 면 resolve

CodeRabbit 이 자동 resolve 하는 경우가 있어 `isResolved` 확인 후 분기한다.

**reject 한 thread 는 여기서 바로 닫지 않는다** — 아래 규칙대로 사용자에게 먼저 묻고, 답을 받은 뒤에 이 mutation 을 쓴다.

```bash
gh api graphql -f query='
  mutation($t: ID!) {
    resolveReviewThread(input: {threadId: $t}) { thread { isResolved } }
  }' -F t="<thread id>"
```

## accept · defer · reject 규칙

지적은 셋 중 하나다. **"맞나"** 와 **"지금 이 PR 에서 고치나"** 는 다른 질문이고, 그 둘을 갈라야 세 갈래가 나온다.

| | 지적이 맞나 | 이 PR 에서 고치나 | reply | resolve | 이슈 |
|---|---|---|---|---|---|
| **accept** | O | O | fix commit hash | O | — |
| **defer** | O | X (범위 밖) | 사유 + 추적처 | **O** | **만들거나 보강** |
| **reject** | X | — | reject 사유 + 되돌릴 여지 | **사용자 확인 후 O** | — |

**셋 다 결국 resolve 한다.** 이 레포는 conversation 이 전부 해소돼야 머지할 수 있어, thread 하나가 남으면 PR 이 거기서 멈춘다. 다만 **reject 는 닫기 전에 사용자에게 묻는다** (아래).

- **accept**: reply 에 fix commit hash 명기 (예: "Accepted. Fixed in `371b5ba`."). 자동 resolve 안 됐으면 resolve 까지.
- **제안과 다르게 고쳤으면 그 이유를 적는다.** 지적은 맞지만 제안된 해법이 좁을 때가 있다 — 왜 다른 방식을 택했는지 남겨야 리뷰어가 재평가할 수 있다.
- **reject**: reply 에 reject 이유 (예: "테스트 규약의 '셋업 hook 으로 stub 상태를 미리 채우지 않는다' 와 충돌").
- 한 thread 가 여러 commit 으로 해소됐으면 해시를 모두 명기한다.

### reject — 닫기 전에 사용자에게 보여준다

**reject 를 열어 두지 않는다.** 예전 규칙은 "사용자가 검토할 기회를 보존한다" 며 열어 뒀는데, 그 목적은 맞지만 수단이 틀렸다 — 열어 둔 thread 는 기록이 아니라 **차단**이다. 실제로 PR #351 에서 reject 2건이 머지를 막았다.

**그렇다고 조용히 닫지도 않는다.** reject 는 셋 중 유일하게 *판단*이 갈리는 갈래다. accept 는 고친 것이고 defer 는 추적처가 남지만, reject 는 "안 고친다" 로 끝나므로 그 판단이 맞는지 볼 사람이 필요하다.

**그래서 `AskUserQuestion` 으로 선택을 받는다.** reject 사유를 요약해 보여주고, 답을 받은 뒤에 움직인다.

- 채팅에 **reject 대상과 사유를 짧게** 정리한다(thread 당 한두 줄).
- `AskUserQuestion` single-select 로 묻는다. 옵션은 이 셋:
  - **reject 하고 resolve** (Recommended) — 판단을 그대로 두고 닫는다
  - **지적을 받아들여 고친다** — accept 로 전환해 코드를 고치고 resolve
  - **열어 둔다** — 사용자가 직접 더 볼 때. 머지가 막힌다는 점을 함께 알린다
- reject 가 여럿이면 **thread 마다 한 질문**으로 나눈다(한 질문에 4개 옵션 한도라 묶으면 사유가 뭉개진다).

reply 말미에는 되돌릴 여지를 남긴다. 닫되 못 박지 않는다.

```text
판단이 다르시면 그쪽을 따르겠습니다. resolve 합니다.
```

### defer — 맞는 지적을 후속으로 넘길 때

지적이 맞는데 이 PR 에서 고치는 것이 틀린 경우가 있다. 다른 PR 이 만든 코드거나, 한 도메인만 고치면 경계가 오히려 늘거나, 범위가 이 PR 의 주제와 다른 경우다.

**그때도 resolve 한다.** 열어 두면 PR 이 계속 막히는데, 막아서 얻는 것이 없다 — 어차피 이 PR 에서 안 고친다. 대신 **추적을 이슈로 옮긴다.** reply 에 그 이슈 번호를 적어, thread 를 닫아도 일이 사라지지 않게 한다.

**이슈는 먼저 찾고, 없을 때만 만든다.** 같은 주제가 이미 이슈나 PR 로 있는데 새로 파면 추적처가 둘이 되고, 둘 다 반쪽만 갱신된다.

```bash
# 1) 기존 이슈·PR 을 먼저 뒤진다 — 닫힌 것까지 본다(이미 다뤘는데 놓쳤을 수 있다)
gh issue list --state all --search "<핵심어>" --limit 20 --json number,title,state -q '.[] | "#\(.number) [\(.state)] \(.title)"'
gh pr list --state all --search "<핵심어>" --limit 20 --json number,title,state -q '.[] | "#\(.number) [\(.state)] \(.title)"'
```

- **있으면 보강한다.** 새로 만들지 않고 그 이슈에 코멘트로 얹는다 — 어떤 PR 의 어느 리뷰에서 나왔는지, 원문 지적이 무엇인지, 이 PR 에서 왜 안 고쳤는지. 본문의 범위가 실제로 넓어졌으면 본문도 고친다.
- **없으면 만든다.** `/issue` 규칙을 따르되 본문에 **출처(PR 번호·thread 링크)** 와 **왜 그 PR 에서 안 고쳤는지**를 반드시 남긴다. 그게 없으면 나중에 "이건 왜 따로 있지" 가 된다.
- **판단이 갈리면 만들지 않고 사용자에게 묻는다.** 이슈를 새로 파는 것은 남의 백로그를 늘리는 일이라, 애매하면 여는 쪽이 아니라 묻는 쪽이 맞다.

reply 는 이렇게 닫는다.

```text
지적 자체는 맞습니다. 다만 이 PR 이 만든 것도, 바꾸는 것도 아닙니다.
(근거 — diff 에 없다 / 다른 PR 이 만들었다 / 한 도메인만 고치면 경계가 는다)

추적: #280 에 얹었습니다. resolve 합니다.
```

## 주의

- owner/repo 는 하드코딩하지 않는다 — 각 조회 블록이 현재 워크트리의 origin 에서 좌표를 재도출한다. GraphQL 은 owner·name 을, REST 경로는 nameWithOwner 를 변수로 넘긴다.
- nitpick 반영 여부·범위는 선택적이다. actionable thread 와 달리 강제가 아니므로 반영할지 사용자와 합의하고 진행한다.
- 리뷰 언어는 `.coderabbit.yaml` 의 `language` 가 정한다 (이 레포는 `ko-KR`). 설정 변경은 **다음 리뷰부터** 적용되고 이미 달린 리뷰는 그대로다.

$ARGUMENTS

현재 브랜치 PR 의 CodeRabbit 리뷰를 처리한다 — 인라인 review thread 와 review body 의 nitpick 을 모두 조회해 평가하고, accept·reject 를 reply·resolve 로 남긴다.

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

```bash
gh api graphql -f query='
  mutation($t: ID!) {
    resolveReviewThread(input: {threadId: $t}) { thread { isResolved } }
  }' -F t="<thread id>"
```

## accept · reject 규칙

- **accept**: reply 에 fix commit hash 명기 (예: "Accepted. Fixed in `371b5ba`."). 자동 resolve 안 됐으면 resolve 까지.
- **제안과 다르게 고쳤으면 그 이유를 적는다.** 지적은 맞지만 제안된 해법이 좁을 때가 있다 — 왜 다른 방식을 택했는지 남겨야 리뷰어가 재평가할 수 있다.
- **reject**: reply 에 reject 이유 (예: "테스트 규약의 '셋업 hook 으로 stub 상태를 미리 채우지 않는다' 와 충돌"). **resolve 하지 않는다** — 사용자가 검토할 기회를 보존한다.
- 한 thread 가 여러 commit 으로 해소됐으면 해시를 모두 명기한다.

## 주의

- owner/repo 는 하드코딩하지 않는다 — 각 조회 블록이 현재 워크트리의 origin 에서 좌표를 재도출한다. GraphQL 은 owner·name 을, REST 경로는 nameWithOwner 를 변수로 넘긴다.
- nitpick 반영 여부·범위는 선택적이다. actionable thread 와 달리 강제가 아니므로 반영할지 사용자와 합의하고 진행한다.
- 리뷰 언어는 `.coderabbit.yaml` 의 `language` 가 정한다 (이 레포는 `ko-KR`). 설정 변경은 **다음 리뷰부터** 적용되고 이미 달린 리뷰는 그대로다.

$ARGUMENTS

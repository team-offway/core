# 백오피스 열기 (#343)

`https://api.offway.cloud/admin/` 을 쓸 수 있게 만드는 절차다. **코드 배포만으로는 안 열린다** — 카카오
콘솔 등록과 최초 어드민 지정이 함께 필요하다.

| # | 무엇 | 담당 | 없으면 |
|---|---|---|---|
| 1 | 카카오 개발자 콘솔에 웹 플랫폼·Redirect URI 등록 | **사람** | 로그인 버튼이 카카오에서 거절된다(`KOE006`) |
| 2 | 한 번 로그인해 사용자 ID 확인 | **사람** | 다음 단계의 값을 알 수 없다 |
| 3 | 최초 어드민 마이그레이션 | 코드 | 로그인은 되지만 목록이 전부 `403` |

운영 환경변수는 **배포 워크플로우가 이미 넣는다**(`.github/workflows/deploy.yml`). 사람이 할 일은 1번과
2번뿐이다.

---

## 1. 카카오 개발자 콘솔

**앱을 새로 만들지 않는다.** 지금 모바일 앱이 쓰는 **같은 카카오 앱**에 추가한다. 카카오 회원번호는 앱
단위로 발급돼서, 새 앱을 만들면 같은 사람인데 번호가 달라진다 — 앱에서 로그인한 계정과 백오피스 계정이
서로 다른 사람이 된다.

| 위치 | 무엇을 |
|---|---|
| 앱 설정 > **플랫폼** > Web | 사이트 도메인에 `https://api.offway.cloud` 추가 |
| 제품 설정 > **카카오 로그인** | 활성화 ON (앱에서 쓰고 있으면 이미 켜져 있다) |
| 제품 설정 > 카카오 로그인 > **Redirect URI** | 아래 둘을 등록 |
| 제품 설정 > 카카오 로그인 > 보안 > **Client Secret** | 선택 — 켜면 아래 '환경변수' 절 참고 |

Redirect URI 는 **경로 한 글자까지 정확히 일치**해야 한다. 카카오가 인가 단계와 토큰 교환 단계에서 두 번
대조한다.

```text
https://api.offway.cloud/api/v1/auth/oauth2/kakao/callback
http://localhost:8080/api/v1/auth/oauth2/kakao/callback
```

두 번째는 로컬 개발용이다. 카카오는 `localhost` 를 http 로 허용한다.

### 안 해도 되는 것

- **REST API 키 재발급** — 이미 `KAKAO_REST_API_KEY` 로 주입돼 있고, 웹 코드 교환에 쓰는 것이 정확히 그
  키다. 앱 로그인이 이 키의 존재로 "카카오 앱이 등록됐는가" 를 판별하는 것과 같은 값이다.
- **동의항목 추가** — 회원번호만 쓰고, 그건 동의항목이 아니다. 화면에 뜰 이름은 `admin_account.label` 이
  들고 있다.

---

### 환경변수는 배포가 넣는다

`deploy.yml` 이 `env.prod` 에 직접 적는다 — **비밀이 아니라 공개 주소**라 secret 으로 둘 이유가 없고,
레포에 있어야 카카오 콘솔에 등록한 값과 대조할 수 있다.

```bash
KAKAO_WEB_REDIRECT_URI=https://api.offway.cloud/api/v1/auth/oauth2/kakao/callback
KAKAO_CLIENT_SECRET=${{ secrets.KAKAO_CLIENT_SECRET }}   # 안 켰으면 빈 값이고, 그러면 안 싣는다
```

기본값이 로컬 주소(`http://localhost:8080/...`)라 이 줄이 없으면 운영에서 로그인이 localhost 로
되돌아간다. 개발자가 설정 없이 로컬에서 시험할 수 있게 기본값을 둔 대가라, 배포가 덮어쓴다.

**둘이 비어도 부팅은 막히지 않는다.** 웹 로그인만 비활성이 되고 앱 로그인·나머지 기능은 그대로다
(로컬 실행성 불변식).

로컬에서 카카오 로그인을 시험하려면 `application-secret.properties` 에 `KAKAO_REST_API_KEY` 와
`KAKAO_APP_ID` 만 채우면 된다. 콜백 주소는 기본값이 이미 로컬이다.

---

## 2. 사용자 ID 확인

`https://api.offway.cloud/admin/` 을 열고 **카카오로 로그인**을 누른다.

아직 어드민 명단이 비어 있으므로 로그인 뒤 이런 화면이 뜬다.

```text
백오피스 권한이 없습니다
  <사용자 ID>   [복사]
```

**이 값은 우리 `users.id` 다** — 카카오 회원번호가 아니다. 회원번호를 화면에 띄우지 않는 이유는 다음
단계가 그것을 직접 필요로 하지 않기 때문이다. 아래 마이그레이션이 이 ID 로 `user_identity` 를 찾아
provider 와 식별자를 스스로 꺼낸다.

---

## 3. 최초 어드민 마이그레이션

아무도 어드민이 아니면 아무도 어드민을 추가할 수 없다. 그래서 첫 한 명만 마이그레이션이 넣는다.

`src/main/resources/db/migration/V{타임스탬프}__grant_first_admin.sql`:

```sql
-- 최초 어드민 (#343). 2번에서 확인한 사용자 ID 를 넣는다.
--
-- 값을 직접 박지 않고 user_identity 에서 꺼내는 이유:
--   (a) provider 와 sub 를 사람이 옮겨 적다 틀릴 여지를 없앤다
--   (b) 그 사용자가 없는 환경(로컬·테스트)에서는 0행이 들어가 아무 일도 일어나지 않는다
--
-- user_id 는 BINARY(16) 이라 문자열과 바로 비교되지 않는다. 하이픈을 떼고 UNHEX 로 맞춘다.
INSERT INTO admin_account (provider, provider_user_id, label)
SELECT ui.provider, ui.provider_user_id, '박세빈'
  FROM user_identity ui
 WHERE ui.user_id = UNHEX(REPLACE('<2번에서 복사한 사용자 ID>', '-', ''))
 LIMIT 1;
```

**넣은 뒤에는 다시 로그인해야 한다.** 역할이 토큰에 실려 있어, 이미 발급된 토큰은 명단이 바뀌어도
그대로다. 반대 방향도 같다 — 명단에서 뺀 사람은 토큰이 만료될 때까지 어드민으로 남는다(재발급 시점에
다시 대조하므로 최대 access 토큰 수명만큼이다).

### 두 번째부터

첫 한 명이 생기면 그 뒤로는 마이그레이션이 필요 없다. 같은 표에 행을 더하면 되고, 그 사람도 한 번
로그인해 `user_identity` 에 흔적을 남긴 뒤여야 한다.

---

## 확인

```bash
# 화면이 인증 없이 열린다 (HTML 에는 비밀이 없다)
curl -s -o /dev/null -w '%{http_code}\n' https://api.offway.cloud/admin/

# 데이터는 잠겨 있다 — 토큰이 아예 없으면 401, 어드민이 아닌 토큰이면 403
curl -s -o /dev/null -w '%{http_code}\n' https://api.offway.cloud/api/v1/admin/curated-links

# 로그인 시작이 카카오로 보낸다
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' \
  https://api.offway.cloud/api/v1/auth/oauth2/kakao
```

마지막 줄이 `localhost` 를 가리키면 배포 환경변수가 안 들어간 것이다.

---

## 알아둘 것

- **access 토큰이 만료되면(기본 1시간) 다시 로그인해야 한다.** refresh 토큰을 브라우저에 두지 않기로
  했기 때문이다 — 수명이 60일이라 잃었을 때의 대가가 크고, 백오피스는 하루 몇 번 여는 화면이다.
  카카오가 이미 로그인돼 있어 다시 들어오는 것은 클릭 한 번이다.
- **토큰은 탭을 닫으면 사라진다**(`sessionStorage`). 공용 PC 에서 다음 사람에게 남지 않게 한 것이다.

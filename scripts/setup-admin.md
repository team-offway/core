# 백오피스 열기 (#343)

`https://api.offway.cloud/admin/` 을 쓸 수 있게 만드는 절차다. **코드 배포만으로는 안 열린다** — 카카오
콘솔 등록과 최초 어드민 지정이 함께 필요하다.

| # | 무엇 | 담당 | 없으면 |
|---|---|---|---|
| 1 | 카카오 개발자 콘솔에 웹 플랫폼·Redirect URI 등록 | **사람** | 로그인 버튼이 카카오에서 거절된다(`KOE006`) |
| 2 | 한 번 로그인해 사용자 ID 확인 | **사람** | 다음 단계의 값을 알 수 없다 |
| 3 | 어드민 명단에 넣기 (DB 직접) | **사람** | 로그인은 되지만 목록이 전부 `403` |

운영 환경변수는 **배포 워크플로우가 이미 넣는다**(`.github/workflows/deploy.yml`). 셋 다 사람이 하는
일이고, 코드 쪽에 남은 일은 없다.

> **1번·3번은 끝났다**(2026-08-31 ~ 09-01). Client Secret 을 켜 두고 `KAKAO_CLIENT_SECRET` 을 GitHub
> 시크릿에 등록했으며, 최초 3명도 명단에 들어갔다 — 아래 기록 참고.

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
단계가 그것을 직접 필요로 하지 않기 때문이다. 아래 쿼리가 이 ID 로 `user_identity` 를 찾아
provider 와 식별자를 스스로 꺼낸다.

---

## 3. 명단에 넣기

**마이그레이션으로 넣지 않는다.** 한 번 만들었다가 접었다(PR #374).

- 마이그레이션의 값어치는 "DB 를 새로 만들어도 재현된다" 인데 **여기서는 성립하지 않는다.** 운영에만
  있는 UUID 로 조인하므로 다른 DB 에서는 언제나 0행이고, 운영 DB 를 새로 말아도 그 사람들은 새 UUID 로
  다시 가입한다. 스키마 변경이 아니라 운영 데이터 한 번 넣기였다.
- **어드민 명단은 바뀌는 데이터다.** 적용된 `V__` 는 수정도 삭제도 못 하는데(checksum), 나중에 누굴
  빼면 그 파일이 남아 현재 상태와 다른 말을 한다. 관리 화면이 없어 어차피 표를 직접 고치게 되므로,
  첫 번째만 마이그레이션이면 일관성도 없다.

그래서 **DB 에 직접 넣고, 절차와 기록을 여기 남긴다.** 이 문서가 마이그레이션이 하던 역할을 대신한다.

### 접속

```bash
ssh -i ~/.ssh/piki-new/offway-tokyo.pem ubuntu@$(dig +short api.offway.cloud | tail -1)
```

> 키는 AWS 키페어 **`offway-tokyo`**(ap-northeast-1) 다. `~/.ssh/config` 의 `team3` 는 **없어진 서울
> 인스턴스**(`3.37.172.220`)를 가리키므로 그걸 믿지 않는다. 실제 서버는 도쿄 리전이고, 사용자는
> `ubuntu` 다.

### 넣기

```bash
cd ~/offway
DBU=$(grep -E "^DB_USERNAME=" env.prod | cut -d= -f2-)
DBP=$(grep -E "^DB_PASSWORD=" env.prod | cut -d= -f2-)
DBNAME=$(grep -E "^DB_URL=" env.prod | cut -d= -f2- | sed -E 's|\?.*$||' | sed -E 's|.*/||')
docker exec -i offway-mysql mysql --default-character-set=utf8mb4 -u"$DBU" -p"$DBP" "$DBNAME"
```

```sql
-- 2번에서 확인한 사용자 ID 와, 감사 흔적에 남길 이름을 짝지어 넣는다.
--
-- provider 와 회원번호를 직접 박지 않고 user_identity 에서 꺼낸다 — 화면이 알려주는 값은 users.id 이고
-- 이 표의 키는 provider+sub 라, 사람이 회원번호를 따로 찾아 옮겨 적으면 틀릴 여지가 생긴다. 그 실수는
-- "로그인은 되는데 계속 403" 으로만 보여 원인을 짚기 어렵다.
--
-- user_id 는 BINARY(16) 이라 문자열과 바로 비교되지 않는다. 하이픈을 떼고 UNHEX 로 맞춘다.
INSERT IGNORE INTO admin_account (provider, provider_user_id, label)
SELECT ui.provider, ui.provider_user_id, seed.label
  FROM (
        SELECT UNHEX(REPLACE('<사용자 ID>', '-', '')) AS user_id, '<이름>' AS label
        UNION ALL
        SELECT UNHEX(REPLACE('<사용자 ID>', '-', '')), '<이름>'
       ) AS seed
  JOIN user_identity ui ON ui.user_id = seed.user_id;
```

**들어간 행 수를 반드시 센다.** `user_id` 를 하나만 잘못 적어도 그 행은 조용히 빠지는데 쿼리는 성공으로
끝난다. 그 사람만 계속 403 이 되고 로그에는 아무 흔적이 없다.

```sql
SELECT id, provider, label, created_at FROM admin_account ORDER BY id;
```

### 빼기

```sql
DELETE FROM admin_account WHERE label = '<이름>';
```

**바로 막히지 않는다.** 역할이 토큰에 실려 나가므로, 뺀 사람은 **access 토큰이 만료될 때까지**(기본
1시간) 어드민으로 남는다. 재발급 시점에 다시 대조하므로 그 이상은 아니다. 즉시 끊어야 하면 그 사용자의
refresh 토큰까지 폐기해야 한다.

### 넣은 뒤에는 각자 다시 로그인해야 한다

같은 이유다 — 이미 손에 든 토큰에는 `ADMIN` 이 없다. 로그아웃하고 다시 들어와야 새 토큰에 실린다.

### 기록

| 날짜 | 이름 | 비고 |
|---|---|---|
| 2026-09-01 | 박세빈 · 조영찬 · 이예빈 | 최초 3명 (#343) |

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

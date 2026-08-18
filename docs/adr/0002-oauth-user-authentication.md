# ADR 0002 — OAuth 인증 기반 User 도메인 (게스트 폐기)

- 상태: 채택(2026-07-29)
- 대상 이슈: #34 (기존 "게스트 식별 기반 사용자" → 범위 전환)
- 영향 이슈: #7(에픽) · #89 · #90 · #91 · #41
- 관련: `user/`, `itinerary/domain/Course`, `common/response/ApiResponseBody`, `docs/specs/api-spec.md`

## 맥락

`X-Guest-Id` 헤더가 `CourseStorageController` 에 로컬 상수로 박혀 있고, 서버는 그 값을 **발급하지도 검증하지도 않는다**. 아무 문자열이나 보내면 그 사람이 된다.

여기에 #89(연차 영속)가 얹히면 서버가 그 문자열을 키로 남은 연차·사용내역까지 저장하게 된다. 그 시점의 "게스트"는 **자격증명만 없는 유저 레코드**다. 별도 개념으로 유지할 이유가 없다.

## 결정

| 결정 | 내용 |
|---|---|
| 게스트 개념 | **폐기.** 첫 진입부터 OAuth 로그인 강제 |
| 가입 수단 | Google · Kakao · Apple (OAuth 만) |
| FE | Flutter 네이티브 앱 (iOS/Android) |
| 인증 흐름 | **클라이언트 주도** — 앱이 provider SDK 로 ID 토큰 획득 → 서버는 검증만 |
| 서버 토큰 | access JWT(1h) + refresh(60일, DB 저장·회전) |
| 내부 식별자 | **UUID** (`BINARY(16)`, 시간정렬) |

### 왜 클라이언트 주도인가

Flutter 앱이므로 서버 주도 리다이렉트(`oauth2Login()`)가 필요 없다. `google_sign_in` · `kakao_flutter_sdk` · `sign_in_with_apple` 셋 다 **OIDC ID 토큰**을 돌려주므로 서버는 세 provider 를 하나의 검증 경로로 처리한다. 쿠키·딥링크·리다이렉트 URL 관리가 전부 빠진다.

### 왜 refresh 를 넣는가

access 만료를 길게 잡고 refresh 를 생략하는 안의 전제는 "만료되면 앱이 provider SDK 로 조용히 재로그인하면 된다"이다. Google 은 `signInSilently()`, Kakao 는 자체 갱신이 되지만 **Sign in with Apple 은 무인 재인증을 지원하지 않는다.** refresh 를 빼면 애플 유저가 만료마다 로그인 화면을 다시 본다.

refresh 를 **Redis 가 아니라 DB** 에 두는 이유는 로컬 실행성 불변식이다. `application-local.properties` 에 Redis 설정이 없고 현재 아무도 Redis 를 쓰지 않아 부팅이 된다. 인증 상태를 Redis 에 얹으면 FE 가 Redis 없이 백엔드를 못 띄운다.

### 새 의존성 없음

`spring-boot-starter-security-oauth2-client` 가 이미 있고, 그 안의 `spring-security-oauth2-jose`(Nimbus)가 JWKS 기반 ID 토큰 검증(`NimbusJwtDecoder.withJwkSetUri`)과 자체 JWT 서명(`NimbusJwtEncoder`)을 모두 제공한다.

## 데이터 모델

FK 제약 없이 raw ID + 조회 인덱스만 둔다(persistence-convention).

| 테이블 | 컬럼 | 제약 |
|---|---|---|
| `users` | `id BINARY(16)` PK · `nickname VARCHAR(50)` · `created_at` · `updated_at` | — |
| `user_identities` | `id BINARY(16)` PK · `user_id BINARY(16)` · `provider VARCHAR(20)` · `provider_user_id VARCHAR(255)` · `created_at` | `UNIQUE(provider, provider_user_id)` · `KEY idx_user_id` |
| `refresh_tokens` | `id BINARY(16)` PK · `user_id BINARY(16)` · `token_hash VARCHAR(64)` · `expires_at` · `created_at` | `UNIQUE(token_hash)` · `KEY idx_user_id` |

**`user_identities` 를 분리한 이유 — `sub` 으로만 매칭한다.**
이메일로 provider 계정을 매칭하면 안 된다. Apple Private Relay 는 익명 주소를 주고, Kakao 는 이메일 동의를 거부할 수 있어 값이 아예 없을 수 있다. ID 토큰의 `sub` 만이 안정적인 키다. 테이블을 분리해두면 나중에 한 유저에 provider 를 여러 개 붙이는 계정 연결도 스키마 변경 없이 열린다.

**JPA 연관관계를 쓰지 않는다.**
`User` ↔ `UserIdentity` 가 생명주기를 공유하긴 하나 **항상 같이 로드되지 않는다** — 로그인은 identity → user 단방향 조회가 주 경로다. 규약의 애그리거트 조건에 맞지 않으므로 셋 다 독립 엔티티 + raw ID.

**UUID 저장은 `BINARY(16)`.**
MySQL·H2 양쪽에서 동작하고 Hibernate 의 UUID 기본 매핑이다. 생성은 `@UuidGenerator(style = TIME)` — 랜덤 v4 는 InnoDB 클러스터드 인덱스를 파편화시키는데, 설정 한 줄 차이라 처음부터 시간정렬로 둔다.

**refresh 는 원문을 저장하지 않는다.**
`token_hash` 에 SHA-256 해시만 저장한다. DB 유출 시 토큰이 그대로 쓰이는 걸 막는다.

## 패키지 구조

```text
user/
├── controller/
│   ├── AuthController        @RestController @RequestMapping("/api/v1/auth")
│   ├── AuthApi               OpenAPI 인터페이스
│   ├── DevAuthController     @Profile("local") 전용
│   └── dto/                  LoginRequest · ReissueRequest · TokenResponse
├── service/
│   ├── AuthService           로그인·재발급·로그아웃 조율
│   ├── TokenIssuer           자체 JWT 발급·검증
│   └── dto/                  LoginCommand · IssuedToken
├── domain/
│   ├── User · UserIdentity · RefreshToken
│   ├── AuthProvider          enum — 상수별 issuer·jwksUri 보유
│   └── UserErrorCode · UserException
├── repository/               각 엔티티 Repository(port) + Impl + JpaRepository
├── infrastructure/oidc/
│   ├── OidcTokenVerifier     port
│   └── NimbusOidcTokenVerifier  adapter (JWKS)
└── config/
    ├── SecurityConfig                기존 permitAll 교체
    ├── JwtAuthenticationFilter
    ├── ApiAuthenticationEntryPoint   401 을 ApiResponseBody 로 (§에러 처리)
    ├── ApiAccessDeniedHandler        403 을 ApiResponseBody 로 (§에러 처리)
    └── LoginUserArgumentResolver + @LoginUser
```

### `AuthProvider` enum

상수별로 `issuer` · `jwksUri` 를 보유해 provider 분기를 없앤다(CLAUDE.md 다형성 원칙).

```java
GOOGLE("https://accounts.google.com", "https://www.googleapis.com/oauth2/v3/certs")
KAKAO ("https://kauth.kakao.com",     "https://kauth.kakao.com/.well-known/jwks.json")
APPLE ("https://appleid.apple.com",   "https://appleid.apple.com/auth/keys")
```

**audience(클라이언트 ID)는 enum 이 아니라 설정으로 뺀다.** 환경별로 다르고, 특히 Google 은 iOS/Android 클라이언트 ID 가 달라 값이 복수다. `offway.auth.oidc.<provider>.audiences` 로 주입한다.

## 인증 흐름

### 로그인 — `POST /api/v1/auth/login`

```jsonc
// 요청
{ "provider": "APPLE", "idToken": "eyJ...", "nickname": "세빈" }  // nickname optional
// 응답 200
{ "status": 200, "code": "OK", "data": { "accessToken": "...", "refreshToken": "...", "expiresIn": 3600 } }
```

1. `OidcTokenVerifier.verify(provider, idToken)` — JWKS 서명 + `iss`·`aud`·`exp` 검증
2. `sub` 추출 → `user_identities` 조회
3. 없으면 `User` + `UserIdentity` 생성 (최초 로그인 = 가입)
4. access JWT(1h) 서명 + refresh(60일) 발급·저장

**`nickname` 을 요청에 optional 로 두는 이유.**
Google 은 ID 토큰에 `name`, Kakao 는 `nickname` 클레임이 온다. 그러나 **Apple 은 ID 토큰에 이름을 주지 않는다** — 최초 인증 응답에만, 그것도 사용자가 제공을 선택했을 때만 온다. 앱이 그 시점에 받아 넘기지 않으면 애플 유저는 영구히 이름이 없다. 서버는 요청 `nickname` → 토큰 클레임 → 기본값 순으로 채운다.

신규 가입도 `200` 으로 응답한다. 생성되는 건 세션이지 클라이언트가 URL 로 가리킬 리소스가 아니므로 `201` 을 쓰지 않는다.

### 재발급 — `POST /api/v1/auth/reissue`

해시로 조회 → 유효하면 **회전**(기존 행 폐기 + 새 access·refresh 발급).

**폐기된 refresh 가 다시 오면 해당 유저의 refresh 를 전부 삭제한다.** 정상 클라이언트는 폐기된 토큰을 재사용하지 않으므로 탈취 정황이다. 회전을 하는 이유 자체가 이 감지라, 회전만 넣고 감지를 빼면 의미가 절반이다.

### 로그아웃 — `POST /api/v1/auth/logout`

해당 유저의 refresh 를 폐기한다. **access 는 만료(1h)까지 유효하다** — stateless JWT 의 대가이며 API 문서에 명시한다.

### 요청 인증

`JwtAuthenticationFilter` 가 `Authorization: Bearer <access>` 를 검증해 `SecurityContext` 에 `userId`(UUID) 를 넣는다. 컨트롤러는 `@LoginUser UUID userId` 로 받는다.

이 필터가 #41 의 MDC `userId` 연결점이 된다 (현재 `"guest"` 고정).

### 전면 인증 전환은 2단계로 나눈다

목표 상태는 `anyRequest().authenticated()` 다. 다만 **지금 잠그지 않는다.**

| 단계 | 접근 정책 | 시점 |
|---|---|---|
| 1단계 (이 ADR) | `/api/v1/auth/logout` 만 authenticated, 그 외 permitAll | 지금 |
| 2단계 | `anyRequest().authenticated()` + 공개 경로 목록(`auth/login`·`reissue`·`dev-login`·swagger·h2·actuator·`/inventory`) | FE 가 provider 클라이언트 ID 확보 후 |

이유는 실 provider 토큰을 만들 주체가 아직 없다는 것이다. 플러터 앱이 나와야 Google·Kakao·Apple SDK 로 ID 토큰을 받을 수 있고, 그전에 전면 잠금을 걸면 **apidog 실호출 검증(#42)이 막힌다.** 코드는 완성돼 있으므로 전환은 matcher 두 줄을 뒤집는 작업이다.

로그아웃만 예외로 잠그는 건 타협이 아니라 필수다 — 누구의 토큰을 폐기할지 알아야 하므로 permitAll 로 열면 `@LoginUser` 가 null 로 들어와 서버 오류가 된다. 덕분에 **401 공통 래퍼 계약은 1단계에서도 통합 테스트로 검증된다.**

2단계에서 함께 해야 할 일: 기존 통합 테스트 13개(약 70개 호출)에 `Authorization` 헤더 부착.

## 로컬 실행성

**불변식**: local 프로파일에서 시크릿·외부 인프라 없이 부팅 가능해야 한다.

OAuth 를 강제하면 FE 가 로컬에서 실 provider 토큰 없이는 **어떤 API 도 못 부른다.** 다음 둘로 해결한다.

1. **개발용 로그인** — `POST /api/v1/auth/dev-login`, `@Profile("local")` 전용. provider 검증 없이 유저를 만들고 토큰만 발급한다. 빈 자체가 prod 에 존재하지 않아 경로가 아예 안 열린다.
2. **JWT 서명키** — `application-local.properties` 에 개발용 고정값을 박고, prod 는 환경변수 필수. 불변식은 "local 에서 시크릿 없이 부팅"이므로 충족된다.

provider 클라이언트 ID(audience)가 비어 있어도 부팅은 되고, 해당 provider 로그인만 `USER-002` 로 실패한다.

## 에러 처리

| code | category | HTTP | 상황 |
|---|---|---|---|
| `USER-001` | UNAUTHORIZED | 401 | ID 토큰 검증 실패(서명·만료·issuer 불일치) |
| `USER-002` | BAD_REQUEST | 400 | 지원하지 않거나 설정되지 않은 provider |
| `USER-003` | UNAUTHORIZED | 401 | refresh 토큰 무효·만료·폐기됨 |
| `USER-004` | UNAUTHORIZED | 401 | access 토큰 무효·만료 |
| `USER-005` | EXTERNAL_API | 502 | provider JWKS 조회 실패 |

`USER-005` 를 분리한 이유: "네 토큰이 틀렸다(401)"와 "구글이 안 뜬다(502)"는 클라이언트가 취할 행동이 완전히 다르다. 전자는 재로그인, 후자는 재시도다.

`ErrorCategory.UNAUTHORIZED` 는 이미 존재하므로 추가하지 않는다. message 는 전부 사용자 대면 고정 문구로 두고, 검증 실패의 구체 사유는 로그·cause 체인에만 남긴다.

### FE 매핑 계약

모든 실패는 성공과 동일한 `ApiResponseBody` 로 나간다. FE 는 이 JSON 을 보고 유저에게 내릴 문구를 결정한다.

```jsonc
{ "status": 401, "data": null, "detail": "로그인이 만료되었습니다.", "code": "USER-004", "pageResponse": null }
```

**매핑 키는 `code` 다.** `detail` 은 이미 사용자 대면 문구지만 서버가 문구를 다듬으면 FE 분기가 조용히 깨진다. `code` 가 계약(append-only·재사용 금지)이고, `detail` 은 FE 가 자체 문구를 두지 않은 경우의 fallback 으로 본다.

### 필터에서 나는 401 은 `GlobalExceptionHandler` 를 타지 않는다

`JwtAuthenticationFilter` 는 서블릿 필터이므로 `DispatcherServlet` **앞** 에서 동작한다. 여기서 던진 예외는 `@RestControllerAdvice` 가 잡지 못한다. 그대로 두면 Spring Security 기본 401 이 나가고 **body 가 비거나 HTML** 이라 FE 가 매핑할 `code` 가 없다. 가장 자주 마주칠 에러가 하필 래퍼 밖으로 샌다.

따라서 `SecurityConfig` 에 두 핸들러를 등록해 **같은 JSON 을 직접 써 내린다.**

| 핸들러 | 상황 | 응답 |
|---|---|---|
| `AuthenticationEntryPoint` | 토큰 없음·무효·만료 | 401 · `USER-004` |
| `AccessDeniedHandler` | 인증됐으나 권한 부족 | 403 · `COMMON` 계열 |

두 핸들러는 `ObjectMapper` 로 `ApiResponseBody.fail(...)` 를 직렬화하고 `Content-Type: application/json` 을 명시한다. 실패 응답 모양이 컨트롤러 경로와 필터 경로에서 **한 글자도 다르지 않아야** FE 가 분기를 하나로 유지할 수 있다.

`AccessDeniedHandler` 는 지금 권한·롤이 없어 실질적으로 안 타지만, 등록해두지 않으면 나중에 롤이 생기는 순간 403 만 래퍼 밖으로 새는 같은 문제가 재발한다.

## 소유 전환 (`guestId` → `userId`)

`courses.guest_id VARCHAR(64)` → `courses.user_id BINARY(16)`.

규약대로 add → backfill → drop 3단계로 나누되, **운영 배포 전이라 backfill 대상 데이터가 없어 no-op** 이다.

1. `V…__add_course_user_id.sql` — 컬럼 + `KEY idx_user_id` 추가
2. 코드 전환 — `Course.ownedBy(UUID userId, …)` · `CourseRepository` 조회 시그니처 · `CourseStorageController` 헤더 → `@LoginUser`
3. `V…__drop_course_guest_id.sql` — 기존 컬럼 제거

`Course.MAX_GUEST_ID_LENGTH` 와 관련 불변식 검증도 함께 제거된다(UUID 는 길이 검증이 불필요).

## 테스트 전략

| 대상 | 분류 | 내용 |
|---|---|---|
| `AuthProvider` · `User` · `RefreshToken` | 단위 | 불변식·만료 판정·상태 전이 |
| 로그인 → 토큰 발급 → 인증 요청 | 통합 | 응답 contract(`status`·`code`·`data`) 포함 |
| refresh 회전 · 재사용 감지 | 통합 | 폐기된 토큰 재사용 시 전체 무효화 확인 |
| 인증 없는 요청 → 401 | 통합 | SecurityConfig 경로 정책 + **응답이 `ApiResponseBody` 규격인지** |
| 신규 가입 vs 기존 로그인 | 통합 | 같은 `sub` 재로그인 시 유저가 늘지 않음 |

**모킹 정책** — 외부 경계인 `OidcTokenVerifier` 만 stub(`@TestConfiguration` + `@Primary`, default 람다는 throw). 토큰은 실제 `TokenIssuer` 빈으로 발급한다(내부 컴포넌트 모킹 금지).

**기존 테스트 영향** — `CourseStorageIntegrationTest` 의 `X-Guest-Id` 헤더가 전부 `Authorization: Bearer` 로 교체된다.

## 범위 경계

**포함**
- `users` · `user_identities` · `refresh_tokens` + 마이그레이션
- 3사 OIDC ID 토큰 검증 · 로그인 · 재발급 · 로그아웃
- `JwtAuthenticationFilter` · `SecurityConfig` 교체 · `@LoginUser` · 401/403 핸들러
- local 전용 dev 로그인
- `courses` 소유 전환

**범위 밖 (후속 이슈)**
- **전면 인증 전환(2단계)** — provider 클라이언트 ID 확보 후
- provider 콘솔 등록 및 audience 값 주입 (코드가 아니라 등록 작업)
- 실 provider 토큰과의 접점 검증 — 실 ID 토큰은 플러터 앱만 만들 수 있어 FE 연동 시점에 확인된다
- 회원 탈퇴
- 계정 연결(한 유저에 provider 여러 개)
- 권한·롤 (지금은 전원 동일)
- Redis 세션
- `HomeResponse` 의 `"게스트"` → 실제 닉네임 교체 (#89 와 함께)

## 파급 — 문서·이슈 갱신

이 결정으로 게스트 전제가 깨지는 곳들.

| 대상 | 조치 |
|---|---|
| #34 | 제목·본문을 "OAuth 인증 기반 User" 로 전환 |
| #7 (에픽) | 완료기준 "로그인 없이 전체 플로우 진행(게스트)" 폐기 |
| #89 · #90 · #91 | 선행이 "게스트 식별" → "OAuth 인증" 으로 변경, 소유 키가 `userId` |
| `docs/specs/api-spec.md` | 3행(인증 게스트) · 36행(로그인 후순위) · 275행(게스트 토큰 헤더) 갱신 |

## 작업 순서

1. `users` · `user_identities` · `refresh_tokens` 마이그레이션 + 엔티티 + 리포지토리
2. `AuthProvider` enum + `OidcTokenVerifier` port/adapter (+ stub)
3. `TokenIssuer` (access 서명 · refresh 발급·해시)
4. `AuthService` + `AuthController`/`AuthApi` — 로그인·재발급·로그아웃
5. `JwtAuthenticationFilter` + `SecurityConfig` 교체 + `@LoginUser` + 401/403 핸들러
6. local dev 로그인
7. `courses` 소유 전환 (마이그레이션 → 코드 → drop)
8. 문서·이슈 갱신

1~6 과 7 은 PR 을 나눈다 — 7 이 `itinerary` 도메인을 건드리므로 리뷰 단위를 섞지 않는다.

---

## 개정 (2026-08-14) — 앱이 실제로 쏘는 계약에 맞춘다

이 ADR 은 2026-07-29 시점의 판단이다. 그 뒤 플러터 앱이 구현되면서 **위 §인증 흐름의 로그인
계약이 실제와 어긋났다.** 아래가 현재 정본이고, 위 본문 중 충돌하는 부분은 이 절이 이긴다.
나머지(토큰 전략·refresh 회전·재사용 감지·UUID 식별자)는 그대로 유효하다.

### 바뀐 것 1 — 로그인 엔드포인트

| | 이전(ADR 원문) | 지금 |
|---|---|---|
| 주소 | `POST /api/v1/auth/login` | `POST /api/v1/auth/callback/{provider}` |
| provider | 본문 필드 | **경로 변수** (`kakao`·`apple`·`google`, 대소문자 무관) |
| 토큰 필드 | `idToken` | `accessToken` |
| 이름·이메일 | `nickname` | `name` · `email` |
| 응답 | `accessToken`·`refreshToken`·`expiresIn` | + **`isNewUser`** |

**`/auth/login` 은 남기지 않고 갈아탔다.** 이 계약은 dev 에 올라간 적이 없어(PR #93 이
머지되지 않았다) 부르는 클라이언트가 존재하지 않는다. 남겨 둘 이유가 "혹시 몰라서" 뿐인데,
같은 일을 하는 입구가 둘이면 인증처럼 틀리면 비싼 곳에서 규칙이 갈린다.

**`isNewUser` 가 계약의 핵심이다.** 앱이 신규는 온보딩(잔여 연차 입력), 기존은 홈으로 보낸다.
사용자를 만든 그 자리에서 판정해 내린다 — "가입 시각이 방금인가" 같은 사후 비교는 경계값에서
흔들리고, 재로그인이 느린 날 기존 사용자를 온보딩으로 보낸다.

**`providerUserId` 는 받되 신원 판단에 쓰지 않는다.** 앱 계약에 있어 받기는 하지만, 그 값을
믿고 계정을 찾으면 남의 식별자를 적어 그 계정으로 로그인할 수 있다 — 요청 한 번짜리 계정
탈취다. 식별자는 언제나 서버가 provider 에게서 직접 확인한 값을 쓴다.

### 바뀐 것 2 — 카카오는 OIDC 경로가 아니다

원문은 셋 다 "OIDC ID 토큰을 주므로 하나의 검증 경로로 처리한다" 고 적었다. **틀렸다.**
앱은 카카오에서 **액세스 토큰**을 받아 넘기고, 그 토큰에는 신원 정보가 없다.

| provider | 앱이 넘기는 것 | 서버가 하는 일 | 외부 호출 |
|---|---|---|---|
| kakao | 액세스 토큰 | `GET https://kapi.kakao.com/v2/user/me` (Bearer) 로 회원번호 조회 | **있다** |
| apple | identityToken(JWT) | `https://appleid.apple.com/auth/keys` 로 서명·`aud` 검증 | 사실상 없음(JWKS 캐시) |
| google | idToken(JWT) | Google 공개키로 서명·`aud`('웹' 클라이언트 ID) 검증 | 사실상 없음(JWKS 캐시) |

그래서 `OidcTokenVerifier` 단일 port 를 **provider 별 전략**으로 나눴다.

```text
infrastructure/social/  SocialIdentityResolver(port, 서비스가 의존)
                        SocialIdentityVerifier(전략)
                        DelegatingSocialIdentityResolver(supports() 로 위임 — provider 분기 없음)
infrastructure/oidc/    NimbusOidcVerifier   — GOOGLE·APPLE (서명 검증)
infrastructure/kakao/   KakaoIdentityVerifier + KakaoProfileClient(port)/Impl(adapter)
```

분류는 `AuthProvider.oidc()` 의 유무가 표현한다 — 서명 검증에 필요한 값(issuer·JWKS 주소)과
그 방식이 쓰이는 조건이 정확히 같아서, boolean 이나 별도 enum 을 또 두지 않는다.

**카카오 프로필 조회는 캐시하지 않는다.** 붙일 수 없어서가 아니라 붙이면 안 된다. 키가 액세스
토큰이라 사용자 수만큼 무한히 늘고(캐시 키 공간 규칙), 무엇보다 신원 확인이 stale 이면
만료·해지된 토큰을 유효하다고 답하게 되어 그게 곧 인증 우회다. 대신 로그인 1회당 호출 1회로
상한이 잡힌다.

**timeout 3초.** 실측(2026-08-14, n=12, 인증 거부 경로) p90 27ms · 최대 30ms. 정상 조회 분포는
실 토큰이 없어 아직 못 쟀으므로 꼬리에 맞춰 좁히는 대신 여유를 크게 잡았다 — 이 호출이 끊기면
로그인 자체가 실패해 사용자가 앱에 들어오지도 못한다. 앱이 붙으면 p99 로 다시 정한다.

**client secret 은 쓰지 않는다.** 그 값이 필요한 곳은 인가 코드를 액세스 토큰으로 바꾸는 토큰
엔드포인트(`POST /oauth/token`) 하나뿐인데, 그 단계는 앱이 SDK 로 이미 끝냈다. 프로필 조회는
액세스 토큰만 받는다.

### 바뀐 것 3 — 전면 인증 전환은 이미 끝나 있었다

원문의 "2단계 전환"(`anyRequest().authenticated()`)은 **이 PR 을 기다리지 않고 #122 가 먼저
했다.** 8080 을 외부에 열면서 임시 HTTP Basic 게이트를 세웠기 때문이다.

그래서 이 PR 은 전환이 아니라 **자격증명을 하나 더 받는 일**이 됐다. 한 체인에서 둘 다 받는다.

| 수단 | 누가 쓰나 | 실패 시 code |
|---|---|---|
| `Authorization: Bearer <access>` | 앱 사용자 | `USER-004`(재발급하라) |
| `Authorization: Basic ...` | 팀 · Swagger · apidog | `COMMON-401`(자격증명 제시하라) |

**Basic 게이트를 이 PR 에서 걷어내지 않았다.** #122 는 "소셜 로그인이 붙으면 걷어낸다" 는
전제로 들어왔지만, 걷어내는 조건은 로그인이 *존재*하는 것이 아니라 **모든 호출자가 실제
토큰을 들고 오는 것**이다. 앱 배포 전까지 Swagger·apidog 는 provider 토큰을 만들 수 없다.
지금 걷어내면 8080 이 다시 열려 TMAP 하루 50건이 봇 한 마리에 고갈된다 — #122 가 막으려던
바로 그 상황이다.

401 의 code 를 자격증명 종류로 가르는 이유도 여기 있다. 401 하나로 뭉치면 앱이 다음에 뭘
해야 할지 모른다. 반대로 아무것도 안 들고 온 요청에 `USER-004` 를 주면, 있지도 않은 refresh 로
재발급을 시도하는 무한 루프가 된다.

자격증명을 **만들어 주는** 경로(`/auth/callback/*`·`/auth/reissue`·`/auth/dev-login`)만 열려
있다. `/auth/logout` 은 잠긴 채다 — 누구의 토큰을 폐기할지 알아야 한다.

### 바뀐 것 4 — 이메일을 보관한다

`users.email VARCHAR(255) NULL` 을 더했다. Apple 은 최초 로그인 응답에만 주므로 그때 받지
못하면 영영 얻을 수 없다.

NULL 을 허용하고 **UNIQUE 를 걸지 않으며 계정 매칭에도 쓰지 않는다.** 카카오는 이메일 동의를
거부할 수 있고, Apple Private Relay 는 서비스마다 다른 익명 주소를 준다 — 동일성 판단에 못 쓴다.
매칭 키는 여전히 `user_identity(provider, provider_user_id)` 뿐이다.

### 그대로인 것

- 토큰 전략(access 1h + refresh 60일 회전·DB 해시 저장), 재사용 감지와 그 롤백 함정
- UUID(BINARY(16), 시간정렬) 식별자
- `@LoginUser` · `JwtAuthenticationFilter` · 필터 단계 401 을 공통 래퍼로 내리는 처리
- local 전용 개발 로그인(`@Profile("local")`)
- **`courses` 소유 전환(`guest_id` → `user_id`)은 여전히 안 됐다.** 별도 PR 로 남아 있고,
  그 때문에 회원 탈퇴(#271)가 지울 수 있는 범위가 제한된다.

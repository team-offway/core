<!--
OffWay `core` 백엔드의 개발 규약. 항상 로드되는 메인 문서다.
주제별 세부 규약은 `.claude/rules/*.md` 로 분리하고 이 문서 하단에서 import 한다.
-->

# OffWay `core` 프로젝트 컨벤션

- **스택**: Spring Boot 4.1 · Java 25 · Lombok · JPA · Flyway · H2(local)/MySQL(prod) · Redis · Spring Security + OAuth2
- **언어**: 커밋 메시지·PR·문서·주석은 한국어. 코드 식별자는 영어(단, 테스트 메서드명은 한국어 허용 — 테스트 규약 참고).
- **구조**: package-by-feature. 도메인이 뚜렷하게 나뉘고 외부 API 연동을 격리한다.

## 아키텍처 — package-by-feature

`com.offway.core.<domain>` 하위를 아래 레이어로 **철저히 분리**한다 (PIKI 스타일 DDD). 안쪽(domain)이 바깥을 모르고, 바깥이 domain의 port를 구현한다.

```
<domain>/
├── controller/          # presentation — HTTP 진입
│   ├── <D>Controller     @RestController @RequestMapping("/api/v1/<복수형>")
│   ├── <D>Api            # OpenAPI 인터페이스 (api-convention)
│   └── dto/              # <X>Request · <X>Response (API 계약)
├── service/             # application — 유스케이스 조율
│   ├── <D>Service            @Transactional 경계, 흐름만
│   ├── <D>PersistenceService # 외부호출 tx 밖 규칙용 (필요 시)
│   └── dto/                  # Create<D>(command) · <D>Result (내부용)
├── domain/              # domain — 핵심 (rich, 프레임워크 의존 최소)
│   ├── <Entity> · <ValueObject> · <Status>(enum)
│   └── <D>ErrorCode · <D>Exception
├── repository/          # infrastructure(영속) — port + adapter
│   ├── <E>Repository        # port(interface) — 도메인이 의존
│   ├── <E>RepositoryImpl    # @Repository, JpaRepository에 위임
│   └── <E>JpaRepository     # Spring Data
├── infrastructure/      # infrastructure(외부) — 외부 API 어댑터
│   └── <system>/  <Client>(port) + <Client>Impl(adapter) + config
└── event/               # 도메인 이벤트 (선택)
```

**의존 방향**: `controller → service → domain ← (repository·infrastructure가 domain의 port 구현)`
- DTO 2계층: `controller/dto`(API 계약) vs `service/dto`(내부 command/result). domain은 DTO를 모른다.
- 리포지토리 **port/adapter 분리**: `XxxRepository`(interface) ← `XxxRepositoryImpl`(@Repository) → `XxxJpaRepository`(Spring Data).

### 도메인 & 외부 API 소유
| 도메인 | 책임 | 소유 외부 API (`infrastructure/`) |
|---|---|---|
| `leave` | 연차·가용시간(LNT)·샌드위치 | 특일정보 |
| `trip` | 인구감소지역·관광지 추천 (관광 데이터 소유) | TourAPI · 관광빅데이터 |
| `transport` | 교통·동선 | TMAP · TAGO · 코레일 |
| `policy` | 7대 혜택 매칭 (수동 seed) | — |
| `itinerary` | 코스 생성 (trip+transport 조합) | — |
| `region` | 인구감소지역 89 마스터·태그 | — |
| `user` | 사용자·인증 | OAuth (후순위) |
| `common` | 응답 래퍼·예외·config (도메인 아님) | — |

- **외부 API는 소유 도메인의 `infrastructure/`** 에 port+adapter로 둔다. 다른 도메인이 필요하면 **소유 도메인의 service/port를 통해** 얻는다(직접 호출 X).
- 도메인 간 참조는 **raw ID + 서비스 조회** (세부는 `persistence-convention`).

### 엔드포인트 네이밍 (PIKI 준수)
- 베이스 `@RequestMapping("/api/v1/<복수형 명사>")` — `/api/v1/regions` · `/api/v1/courses` · `/api/v1/policies`
- 하위 리소스 `/{id}/sub`, 액션 POST `/{id}/action`, 다단어 **kebab-case**(`/available-time`)

## 로컬 실행성 (불변식)

**designated 브랜치는 `local` 프로파일에서 시크릿·외부 인프라 없이 부팅 가능해야 한다.**

- 로컬은 **H2 인메모리**로 뜬다. 외부 API 키·OAuth 시크릿·실 DB/Redis 가 없어도 **부팅 자체는 막히지 않는다** (실제 외부 호출만 실패).
- 외부 API 클라이언트는 키가 없으면 **비활성/stub 으로 뜨게** 설계한다 (§추상화, `external` port 인터페이스).
- 부팅에 실 키·실 DB 를 강제하는 변경은 금지. 이 불변식을 깨면 FE 가 백엔드를 못 띄우고, CI 스모크(컨텍스트 로드)가 빨간불이 된다.
- 실행: `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`. 운영은 `SPRING_PROFILES_ACTIVE=prod` + 환경변수.

## 성능 · 외부 호출 (핵심 스타일)

**OffWay 는 느리고 불안정한 공공 API 위에 서 있다. 화면 하나가 외부 API 여러 개를 모으므로, 성능은 나중에 붙이는 게 아니라 설계의 일부다.** 클라이언트가 떠안을 일을 백엔드가 미리 끝내둔다 — FE 가 여러 번 부르거나, 받아서 계산하거나, 기다리게 만들지 않는다. 측정 근거는 `docs/adr/0001-external-api-resilience.md`.

### 요청 경로에서 외부 I/O 를 뺀다

- 외부 호출은 **`ExternalDataCache` 위에** 올린다. 값의 신선도 기간을 **데이터 특성에서 도출**해 TTL 로 정한다(월간 발행 → 6시간, 시간당 갱신 → 1시간, 실시간 → 수십 초).
- 89개 지역처럼 **키 공간이 고정이고 느리게 변하는** 값은 `HomeCacheWarmer` 로 **미리 데운다**. 지연 캐시면 첫 요청이 `외부 read-timeout × N` 을 뒤집어쓴다.
- 워밍 주기는 그 캐시 TTL 보다 **짧게**. 같지 않게 둬야 만료 구간이 사용자에게 새지 않는다.

### 팬아웃은 병렬. 순차 루프 금지

- 후보 N 개에 외부 호출을 붙이는 루프는 **동시성 상한을 둔 병렬**로 짠다. `for` 로 순차 호출하면 지연이 N 배로 곱해진다.
- **요청 경로와 워밍 경로의 동시성을 다르게 두지 않는다.** 같은 팬아웃인데 백그라운드만 병렬이면 사용자가 느린 쪽을 탄다.

### 캐시 키 공간의 상한을 먼저 정한다

- 새 캐시를 만들 때 **키가 몇 개까지 늘 수 있는지 먼저 답한다.** 지역 id·시도명처럼 유한하면 그대로 둔다.
- **좌표·정류소 id 처럼 무한한 키**는 상한(LRU 등)과 만료 엔트리 제거를 **함께** 설계한다. TTL 은 값의 신선도만 관리하고 **엔트리를 지우지 않는다** — TTL 이 짧을수록 오히려 빨리 쌓인다.

### 실측하고 숫자를 남긴다

- **timeout 은 median 이 아니라 응답시간 분포의 꼬리에서 정한다.** 여러 번 재보고 최대값의 2배쯤 둔다. 분포 안쪽을 자르면 간헐 실패가 되고, 폴백이 조용히 화면 품질을 떨어뜨린다.
- 새 외부 API 를 붙일 땐 **응답 크기·응답시간·페이지 수를 실호출로 재고** `docs/external-api-inventory.md` 에 남긴다. 응답이 크면 `WebClient` 의 `maxInMemorySize` 상한과 함께 본다.
- 캐시·인덱스처럼 효과를 주장하는 변경은 **전후 숫자**를 남긴다(ADR 0001 형식).

### 조용한 실패를 만들지 않는다

- **빈 응답을 성공으로 캐시하지 않는다.** 값이 없다는 점에서 실패와 결과가 같은데 성공 TTL 로 누르면 무의미한 상태가 그만큼 굳는다. 실패와 같은 짧은 TTL + warn 으로 재시도를 유도한다.
- 외부가 `resultCode` 는 성공인데 결과가 비어 오는 경우가 흔하다. 이건 **예외보다 위험하다** — 예외는 로그에 남지만 빈 응답은 아무 흔적을 남기지 않는다.
- degrade 해서 넘어갈 때도 **왜 degrade 했는지 로그에 남긴다**. 폴백이 정상처럼 보이면 장애를 아무도 모른다.

### DB

- 매 요청 다시 계산·조회하는 것 중 **입력이 안 바뀌는 것**(레퍼런스 데이터, 좌표 기반 정적 그래프)은 부팅 시 1회로 옮긴다.
- 연관관계 컬렉션 조회는 N+1 을 fetch join·`@EntityGraph`·`@BatchSize` 로 차단한다(`persistence-convention`).
- 조회 전용 트랜잭션은 `readOnly = true`. **외부 호출은 트랜잭션 밖에서** — read-timeout 이 길어 커넥션 풀이 고갈된다.

## 객체지향 설계 · 상수화 (핵심 스타일)

이 프로젝트의 기본 스타일이다. 아래를 지킨다.

- **매직 값 금지 → 상수·enum.** 비즈니스 로직에 리터럴(숫자·문자열)을 직접 박지 않는다. 의미 있는 값은 `static final` 상수나 enum 으로 승격한다.
  - 예: LNT 계산의 시간 단위·이동거리 임계치, 캐시 TTL·키, 외부 API base URL·path·param key.
- **분류·상태·타입은 enum.** boolean 플래그·문자열 코드 대신 enum 을 쓰고, 가능하면 **enum 에 행위를 담아**(상수별 메서드) 다형성으로 분기를 없앤다.
  - OffWay 예: `PolicyType`(7대 혜택 — 각 상수가 매칭조건·뱃지문구 보유), `TransportMode`(자가용·KTX·SRT·시외버스 — 상수별 소요시간 계산), `RegionType`.
- **캡슐화 / rich domain.** 필드는 `private`, public setter 금지. 상태 변경·계산은 도메인 객체 메서드로 표현한다. 서비스에 분기가 쌓이면 도메인으로 내린다(anemic 지양).
  - 예: `AvailableTime`(LNT) 값객체가 계산·검증을 직접 소유하고, `SandwichHoliday` 가 "황금 연차인지"를 스스로 판단한다. 서비스는 조율만 한다.
- **객체 생성은 빌더 패턴을 기본으로.** 여러 필드를 조립하는 엔티티·커맨드·응답 객체는 Lombok `@Builder` 로 만든다(생성자 인자 순서 실수 방지·가독성). new·telescoping 생성자 남발 금지.
  - **예외 — 입력에서 계산되는 파생 값객체는 static 팩토리로.** `AvailableTime.of(start, end, transport)` 처럼 결과가 입력에서 도출되는 것은 빌더로 열지 않는다. 빌더로 열면 계산 결과(`travelDays` 등)를 외부가 직접 세팅해 불변식이 깨진다. 이 경계 기준: **조립이면 빌더, 계산이면 팩토리.**
- **추상화 / DIP.** 외부 의존성은 도메인이 **port 인터페이스**에만 의존한다. 구현(adapter)은 `external` 에 격리해 프레임워크·외부 세부가 도메인에 새지 않게 한다.
- **다형성 / 전략.** 변형되는 행위(교통수단별 소요시간, 정책별 매칭 규칙 등)는 `if/else` 타입 스위치 대신 다형성(인터페이스 구현 · enum 전략 · `sealed interface` + pattern matching)으로 표현한다.
- **Java 25 활용.** 불변 값 객체는 `record`, 닫힌 계층은 `sealed interface` + `switch` 패턴 매칭, 상수 집합은 enum. Lombok 은 보일러플레이트(`@Getter`·`@Builder`·`@Slf4j`)에 한정한다.

## Null 처리

- 깊은 `if (x == null)` 중첩을 만들지 않는다. **guard clause + early return** 으로 푼다.
- 부재가 의미 있는 반환값은 `Optional<T>`. 불변식 강제는 `Objects.requireNonNull(x, "...")`.
- 복합 조건은 함수를 분해해 guard clause 여러 줄로 편다.

## DTO ↔ 도메인 매핑

- **매핑 로직은 DTO 자신에 둔다. 별도 Mapper 클래스/빈을 만들지 않는다.**
- 도메인 → 응답 DTO(record): static `from(도메인)`. 예: `LeaveResponse.from(leave)`.
- 요청 DTO → 도메인/커맨드: 인스턴스 `toXxx()`. 외부 응답 → 도메인: 외부 결과 객체의 `toXxx()`.

## 로깅

- Lombok `@Slf4j` 사용.
- **민감 정보 마스킹**: 외부 API 키·토큰·URL 쿼리스트링·사용자 입력 원본을 로그에 그대로 남기지 않는다.
- 레벨: **info**(정상 흐름·클라이언트 계약 위반) / **warn**(외부 호출 실패·재시도) / **error**(예상 못한 서버 버그, 스택 포함).
- SLF4J `{}` placeholder 사용 (`log.info("lnt={}h", hours)`).

## 도메인 용어 (glossary)

- **LNT** — 총 가용 시간(Leave-based Net Time). 연차+공휴일+주말로 산출한 실제 여행 가능 시간.
- **샌드위치 연휴** — 공휴일·주말 사이 평일에 연차를 끼워 최소 연차로 최대 휴식.
- **인구감소지역** — 행안부 고시 89곳. 추천 대상 지역.
- **생활인구** — 등록인구가 아닌 체류 기반 인구. 추천 가중치로 사용.
- **7대 혜택** — 숙박세일페스타·디지털관광주민증·근로자휴가지원·지자체바우처·KTX/SRT할인·로컬100/관광두레·농촌체험/치유관광.

## 의존성 관리

- **버전 단일 진실 원천은 `build.gradle.kts`.** 문서에 버전 숫자를 박지 않는다.
- 새 의존성은 Maven Central 최신 안정 버전을 조회해 추가한다(pre-release 제외). Spring Boot BOM 이 관리하는 의존성은 버전을 명시하지 않는다.

## YAGNI / 가까운 미래

- 가설적 먼 미래를 위한 추상화는 만들지 않는다. 단, 합의된 가까운 후속(Nice-to-have 등)과 충돌하지 않게 설계한다.

## 세부 규약 (작업별 온디맨드 — 해당 작업 시작 전 반드시 읽는다)

컨텍스트 절약을 위해 아래 파일은 자동 로드하지 않는다. 해당 작업을 할 때 **그 파일을 먼저 읽고** 규칙을 따른다.

| 이런 작업을 하면 | 이 파일을 읽는다 |
|---|---|
| 예외/에러코드 정의, 응답 DTO·컨트롤러 응답 포맷 | `.claude/rules/exception-and-response.md` |
| JPA 엔티티·연관관계, Flyway 마이그레이션, 트랜잭션 경계 | `.claude/rules/persistence-convention.md` |
| 컨트롤러·`*Api` 인터페이스·OpenAPI 문서화 | `.claude/rules/api-convention.md` |
| 테스트 작성 (단위·통합·E2E) | `.claude/rules/testing-convention.md` |

## 기계 강제 규칙 (훅)

아래는 문서가 아니라 **훅이 막는다.** 위반하면 편집이 즉시 차단된다.

| 훅 | 막는 것 |
|---|---|
| `.githooks/commit-msg` | 커밋 메시지 타입·형식 (허용 타입의 정본도 이 파일) |
| `.claude/hooks/convention-check.sh` (PostToolUse) | 아래 7종 |

- **적용된 Flyway 마이그레이션 수정** — checksum 이 깨져 부팅이 실패한다. 새 timestamp 로 보정 마이그레이션을 추가한다.
- **마이그레이션의 `FOREIGN KEY`** — UNIQUE·PRIMARY KEY 제약은 정상이므로 FK 만 막는다.
- **`javax.{persistence,validation,servlet,annotation,transaction}` import** — Jakarta 로 옮겨간 것만. `javax.sql`·`javax.crypto` 등 JDK 표준은 통과.
- **`FetchType.EAGER`**
- **HTTP 204** (`HttpStatus.NO_CONTENT`·`noContent()`)
- **`domain/` 의 public setter·`@Setter`·`@Data`**
- **`controller/` 의 `@Transactional`**, **테스트의 `@MockBean`·`@SpyBean`·`@DirtiesContext`·`@ActiveProfiles`·`@TestPropertySource`**

**훅에 넣지 않는 것**: 매직 값·rich domain·DIP·다형성·null 중첩 깊이, 그리고 **§성능·외부 호출 전부**(순차 팬아웃·캐시 키 상한·timeout 근거·조용한 실패)처럼 **판단이 필요한 규칙**. 정규식으로 오탐이 나면 훅 자체가 무시당한다. 이들은 `/pre-pr` 의 self-audit 이 담당한다.

규칙을 추가·변경할 땐 훅 스크립트를 고치고, **기존 소스 전체에 오탐이 없는지 먼저 확인한다.** `--file` 은 파일 하나만 검사하므로 아래처럼 전체를 돌린다. 차단이 1건이라도 나오면 그 규칙은 오탐이다 — 훅에 넣지 않는다.

```bash
for f in $(git ls-files 'src/**/*.java' 'src/**/*.sql'); do
  sh .claude/hooks/convention-check.sh --file "$f" >/dev/null 2>&1 || echo "차단: $f"
done
```

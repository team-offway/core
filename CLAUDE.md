<!--
OffWay `core` 백엔드의 개발 규약. 항상 로드되는 메인 문서다.
주제별 세부 규약은 `.claude/rules/*.md` 로 분리하고 이 문서 하단에서 import 한다.
-->

# OffWay `core` 프로젝트 컨벤션

- **스택**: Spring Boot 4.1 · Java 25 · Lombok · JPA · Flyway · H2(local)/MySQL(prod) · Redis · Spring Security + OAuth2
- **언어**: 커밋 메시지·PR·문서·주석은 한국어. 코드 식별자는 영어(단, 테스트 메서드명은 한국어 허용 — 테스트 규약 참고).
- **구조**: package-by-feature. 도메인이 뚜렷하게 나뉘고 외부 API 연동을 격리한다.

## 아키텍처 — package-by-feature

`com.offway.core.<domain>` 하위를 도메인별로 나눈다. 각 도메인은 내부에 필요한 만큼 `controller / service / domain / repository / dto / exception` 을 둔다.

```
com.offway.core
├── leave        # 연차·가용시간(LNT)·샌드위치 연휴·연차 컨설팅 (순수 도메인 로직 — 테스트 핵심)
├── trip         # 인구감소지역·관광지 추천 (TourAPI)
├── policy       # 7대 여행 지원 혜택 매칭 (수동 적재 데이터)
├── transport    # 교통·동선 (TAGO·TMAP·코레일/SR)
├── itinerary    # 일정표 자동 생성 (leave+trip+transport 조합)
├── external     # 외부 API 클라이언트 격리 (WebClient). API별 서브패키지(external/tour, external/holiday …)
├── user         # 사용자·인증 (Spring Security + OAuth2)
└── common       # 응답 래퍼·예외 primitive·전역 핸들러·config
```

- 도메인 간 참조는 **raw ID + 서비스 계층 조회**로 한다 (세부는 `persistence-convention`).
- 도메인 서비스는 `external` 의 **port 인터페이스**에만 의존한다. 실제 HTTP 호출 구현(adapter)은 `external` 에 격리한다.

## 로컬 실행성 (불변식)

**designated 브랜치는 `local` 프로파일에서 시크릿·외부 인프라 없이 부팅 가능해야 한다.**

- 로컬은 **H2 인메모리**로 뜬다. 외부 API 키·OAuth 시크릿·실 DB/Redis 가 없어도 **부팅 자체는 막히지 않는다** (실제 외부 호출만 실패).
- 외부 API 클라이언트는 키가 없으면 **비활성/stub 으로 뜨게** 설계한다 (§추상화, `external` port 인터페이스).
- 부팅에 실 키·실 DB 를 강제하는 변경은 금지. 이 불변식을 깨면 FE 가 백엔드를 못 띄우고, CI 스모크(컨텍스트 로드)가 빨간불이 된다.
- 실행: `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`. 운영은 `SPRING_PROFILES_ACTIVE=prod` + 환경변수.

## 객체지향 설계 · 상수화 (핵심 스타일)

이 프로젝트의 기본 스타일이다. 아래를 지킨다.

- **매직 값 금지 → 상수·enum.** 비즈니스 로직에 리터럴(숫자·문자열)을 직접 박지 않는다. 의미 있는 값은 `static final` 상수나 enum 으로 승격한다.
  - 예: LNT 계산의 시간 단위·이동거리 임계치, 캐시 TTL·키, 외부 API base URL·path·param key.
- **분류·상태·타입은 enum.** boolean 플래그·문자열 코드 대신 enum 을 쓰고, 가능하면 **enum 에 행위를 담아**(상수별 메서드) 다형성으로 분기를 없앤다.
  - OffWay 예: `PolicyType`(7대 혜택 — 각 상수가 매칭조건·뱃지문구 보유), `TransportMode`(자가용·KTX·SRT·시외버스 — 상수별 소요시간 계산), `RegionType`.
- **캡슐화 / rich domain.** 필드는 `private`, public setter 금지. 상태 변경·계산은 도메인 객체 메서드로 표현한다. 서비스에 분기가 쌓이면 도메인으로 내린다(anemic 지양).
  - 예: `AvailableTime`(LNT) 값객체가 계산·검증을 직접 소유하고, `SandwichHoliday` 가 "황금 연차인지"를 스스로 판단한다. 서비스는 조율만 한다.
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

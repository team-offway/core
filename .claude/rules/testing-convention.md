# 테스트 규약

테스트 작성 시 읽는다. Java · JUnit5 기준.

## 테스트 분류

- **단위 테스트** — Spring·DB 없이 도메인 객체와 순수 함수만 검증. 부팅 비용 0. 입력 형식·정규화·상태 변화·계산 등 **분기 폭이 큰 영역을 여기서 망라**한다. (OffWay 핵심: LNT 계산·샌드위치 연휴 판정·정책 매칭 규칙.)
- **통합 테스트** — 컨트롤러(HTTP 진입)부터 DB 까지 실제 흐름 검증. 외부 호출(TourAPI·TAGO·TMAP 등)은 **stub 빈으로 격리**한다.
- **E2E 테스트** — 외부 의존성을 stub 없이 **실제 호출**해 "실제 외부와 우리 코드의 접점"을 확인. 네트워크 비결정성 탓에 CI 기본 실행에서 제외하고 격리한다.

서비스 단독 테스트는 두지 않는다. 분기는 도메인으로 옮겨 단위 테스트로 내리고, 시나리오·contract 는 통합 테스트로 흡수한다. 서비스에 분기가 쌓이면 도메인이 빈약(anemic)하다는 신호다.

## 테스트 가치 판단 (무엇을 테스트하는가)

**기준 한 줄: 커버리지 숫자가 아니라 "깨지면 비싼가 + 회귀 위험이 큰가 + 읽기·변경이 잦은가".**

| 가치 | 무엇 | 어디서 |
|---|---|---|
| 높음 (반드시) | 도메인 정책·계산·상태 전이 (분기 큰 것) | 단위 |
| 높음 (반드시) | 핵심 유저 여정 (연차 입력→일정 확정, 인증) | 통합 |
| 높음 (반드시) | HTTP 계약 (응답 모양·예외→상태 매핑) | 통합 |
| 높음 (반드시) | 외부 의존성 분기 (성공/실패/timeout) | stub 통합 |
| 낮음 (생략) | 단순 위임·getter·자명한 매핑, 프레임워크 보장(JPA 기본 CRUD) | — |

가치가 낮다고 생략하면 그 판단을 PR·코드에 한 줄 남긴다("빠뜨림"과 "의도적 생략" 구분).

## 분기 위치 결정 트리

```text
이 분기는 무엇을 검사하는가?
(1) 입력 형식·값 범위·null·정규화 → 도메인 생성자/팩토리 → 단위
(2) 상태 변화·계산·정책       → 도메인 메서드      → 단위
(3) DTO ↔ 도메인 매핑          → DTO from()/toXxx() → 단위
(4) "DB 상태 + 결정"(이미 존재?) → 통합
(5) HTTP contract(검증·예외→상태·응답 모양) → 통합
(6) 외부 API 결과 분기(성공/실패/timeout) → 외부 stub + 통합
(7) 실제 외부 의존성과의 접점 → stub 없이 실호출 → E2E (격리·CI 제외)
```

## 작성 원칙

### 단위 테스트
- 위치: 대상 도메인과 같은 패키지(`src/test/java/.../leave/domain/AvailableTimeTest.java`).
- Spring·DB 없이. 분기 망라가 목적 → `@ParameterizedTest` 적극 활용.

### 통합 테스트
- 엔드포인트당 시나리오·contract 검증 3~5건. 분기 망라는 도메인 단위로 내린다.
- **응답 contract(`status`·`code`·`detail`·`data`) 를 단언에 포함**한다. 도메인 단언만으론 직렬화·예외 매핑 회귀를 못 잡는다.
- 검증 실패(400)·비즈니스 예외(409 등) 케이스도 포함.
- **DB 는 초기엔 H2** 로 한다(로컬 실행성·부팅 비용). 실제 MySQL 특성 검증이 필요해지면 Testcontainers 를 별도 작업으로 승격하고 이 파일에 반영한다.

### E2E 테스트
- **격리 필수**: `@EnabledIfEnvironmentVariable(named = "...", matches = ".+")`(환경 있으면 실행) 또는 `@Disabled("이유")` 중 하나를 **반드시** 둔다. 무방비 E2E 금지(CI 에서 실 외부 호출·flaky).
- 네이밍 `*E2ETest`. 비결정성을 단위·통합으로 전파시키지 않는다.

## 모킹 / Stub 정책

- **내부 컴포넌트는 모킹·stub 금지, 실제 빈으로 통합 테스트한다.**
- **외부 호출 경계**(외부 HTTP API 등)만 **프로그래머블 stub** 으로 격리한다. `external` 의 port 인터페이스에 stub 구현을 만들어 `@TestConfiguration` + `@Primary` 로 등록하고, 응답을 람다로 받아 매 테스트가 교체한다.
- **stub 의 default 람다는 throw** 로 둔다 — 명시 세팅을 빠뜨리면 즉시 깨지게 해 "이전 테스트 상태가 살아남는" 함정을 막는다.
- **`@MockBean`·`@SpyBean` 금지** — 내부 컴포넌트 모킹 금지의 구체적 수단이라 함께 막는다. PostToolUse 훅이 기계 강제한다.
- **`@DirtiesContext`·`@ActiveProfiles`·`@TestPropertySource` 금지** — 컨텍스트 캐시를 깨 전체 테스트 시간을 늘린다(통합 테스트는 단일 컨텍스트 공유). 역시 훅이 강제한다.
- **`@TestConfiguration` 은 금지 대상이 아니다** — 위 stub 등록(`@TestConfiguration` + `@Primary`)에 필요하다. `@AutoConfigureMockMvc` 도 정상.

## 네이밍 · 단언

| 분류 | 클래스명 | 예 |
|---|---|---|
| 단위 | `{대상}Test` | `AvailableTimeTest` |
| 통합 | `{시나리오}IntegrationTest` | `LeaveAvailableTimeIntegrationTest` |
| E2E | `{대상}E2ETest` | `TourApiClientE2ETest` |

- **메서드명은 한국어 식별자로 시나리오를 한 문장으로.** Java 는 식별자에 한글을 허용한다.
  ```java
  @Test
  void 연차_1일_금요일이면_LNT는_72시간이다() { ... }
  ```
- **단언은 JUnit5 `Assertions` 기본** (`assertEquals`, `assertThrows`). 컬렉션 비교·객체 그래프 깊은 비교·soft assertion 처럼 표현력 차이가 큰 경우만 **AssertJ**(`assertThat`). 둘 다 `spring-boot-starter-test` 에 포함. 한 메서드에서 두 스타일을 섞지 않는다.

## 셋업 원칙

- `@BeforeEach`/`@BeforeAll` 셋업 hook 으로 fixture·stub 상태를 미리 채우지 않는다. **각 테스트가 자기 시나리오에 필요한 데이터·stub 을 본문에서 직접 만든다** — 메서드 하나만 봐도 시나리오가 완결되게.
- DB 격리는 클래스 레벨 `@Transactional` 자동 롤백으로 해결한다.

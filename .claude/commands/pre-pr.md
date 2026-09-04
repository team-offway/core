PR 올리기 전 최종 점검 — `origin/dev` 와 up-to-date 를 맞추고, 변경 영역에 맞는 테스트를 **테스트 규약대로** 작성·실행하며, 컨벤션·하드코딩 self-audit + 응답 전수 문서화 + (전역 변경 시) blast-radius 까지 확인한 뒤 `/commit`·`/pr` 로 넘긴다. 한 군데라도 막히면 PR 을 만들지 않고 멈춘다.

> **실행 환경**: 셸 블록은 **Bash 도구**로 실행한다.

## 핵심 전제

- **base 는 `origin/dev`.** 이 프로젝트 기본 브랜치는 `dev` 이고, 로컬 `dev` 는 stale 일 수 있어 항상 `origin/dev` 를 기준으로 diff·머지한다 (로컬 `dev` 로 비교하면 머지로 끌려온 남의 커밋까지 섞여 diff 가 부풀려진다).
- **테스트는 `.claude/rules/testing-convention.md` 를 그대로 따른다.** 단위(Spring 없음) + 통합(Testcontainers MySQL) 두 종류. 외부 호출은 port 인터페이스의 프로그래머블 stub 으로 격리한다.
- **구현이 끝난 뒤 호출한다.** 이미 구현된 변경에 대해 테스트 커버리지를 메우고 최종 점검하는 단계다.

## 절차

### 0단계: 작업 위치·base 확정

```bash
CURRENT_BRANCH=$(git branch --show-current)
# detached HEAD 면 빈 문자열이라 아래 dev 비교를 그냥 통과한다. 여기서 끊는다.
[ -n "$CURRENT_BRANCH" ] || { echo "detached HEAD 입니다. 작업 브랜치를 체크아웃한 뒤 다시 실행하세요."; exit 1; }
# fetch 가 실패하면 이후 diff·머지가 stale 한 origin/dev 를 기준으로 돌아 조용히 틀린 결과를 낸다.
git fetch origin dev -q || { echo "git fetch 실패 — origin/dev 가 stale 인 채 진행하지 않는다."; exit 1; }
echo "CURRENT_BRANCH=$CURRENT_BRANCH"
```

- cwd 가 워크트리(작업 브랜치) 안인지 확인한다. `$CURRENT_BRANCH` 가 `dev` 이면 base 브랜치에서 호출한 것 — 작업 워크트리에서 다시 부르라고 안내하고 멈춘다.
- 이후 모든 diff 의 base 는 `origin/dev`.

### 1단계: dev up-to-date 확인

머지 게이트에 막히기 전에 미리 맞춘다.

```bash
BEHIND=$(git rev-list --count HEAD..origin/dev)
echo "dev 미반영 커밋: $BEHIND"
```

- `0` 이면 통과.
- `1` 이상이면 `git merge origin/dev` 한다. 충돌이 나면 사용자에게 보고하고 함께 해소한다 (자동 강제 머지 금지).
- 머지 후에는 아래 테스트·점검을 머지된 상태로 다시 돌린다.

> commit-msg 훅은 `Merge ` 로 시작하는 기본 머지 메시지를 통과시키므로 별도 규격 메시지가 필요 없다 (`.githooks/commit-msg`).

### 2단계: 변경 파일 분석 (origin/dev 기준)

```bash
git diff origin/dev...HEAD --name-only
git diff origin/dev...HEAD --stat
git diff origin/dev...HEAD
```

**우리 변경만** 본다. 머지로 끌려온 남의 커밋 파일은 대상이 아니다.

**테스트 작성 대상 판단:**

| 대상 O | 대상 X |
|---|---|
| `**/domain/**/*.java` (엔티티·값객체·도메인 메서드 — 분기·상태변화·계산) | `**/*Api.java` (OpenAPI 인터페이스) |
| `**/controller/**/*.java` (HTTP contract·예외→status·응답 모양) | `**/config/**/*.java`, `**/*Application.java` |
| `**/controller/dto/**/*.java` (Bean Validation·`from`·`toXxx` 매핑) | `**/service/dto/**/*.java`, `**/*Properties.java` |
| `**/service/**/*.java` (DB 상태+결정·외부호출 분기 → **통합** 으로) | `**/repository/**/*Repository.java`, `**/*JpaRepository.java` (인터페이스) |
| `**/repository/**/*RepositoryImpl.java` (구현 로직 있을 때) | `.github/**`, `.claude/**`, `*.md`, `*.yml`, `build.gradle.kts`, 마이그레이션 SQL |
| `**/infrastructure/**/*Impl.java` (외부 API 어댑터 — 응답 파싱·실패 분기) | `**/infrastructure/**/*Client.java` (port 인터페이스) |

파일명만 보지 말고 **diff 내용**으로 판단한다. 도메인 이름은 diff 에서 동적으로 추출한다 (하드코딩 금지).

대상이 없으면 → 바로 **4단계**(self-audit)로.

### 3단계: 테스트 작성 — 테스트 규약 준수

**분기 위치 결정 트리** (이 분기는 무엇을 검사하는가):

1. 입력 형식·값 범위·null·정규화 → 도메인 생성자·팩토리 → **단위 테스트**
2. 상태 변화·계산·정책 → 도메인 메서드 → **단위 테스트**
3. DTO ↔ 도메인 매핑 → DTO 의 `from()`·`toXxx()` → **단위 테스트**
4. "DB 상태 + 결정"(이미 존재하는가) → **통합 테스트**
5. HTTP contract (Bean Validation·예외→status·응답 모양) → **통합 테스트**
6. 외부 API 결과 분기 (성공·실패·timeout) → 외부 stub + **통합 테스트**
7. 실제 외부 의존성과의 접점 → stub 없이 실호출 → **E2E** (격리 필수, CI 제외)

> 서비스에 분기가 쌓이면 도메인이 빈약(anemic)하다는 신호 — 분기를 도메인 메서드로 옮겨 단위 테스트로 내린다. **서비스 단독 테스트는 만들지 않는다.**

**단위 테스트**: 대상 도메인과 같은 패키지, Spring·DB 의존 0, `@ParameterizedTest` 로 분기 망라.

**통합 테스트**: `@SpringBootTest`(+ `@AutoConfigureMockMvc`), Testcontainers MySQL. 외부 호출만 port 인터페이스의 프로그래머블 stub 으로 격리(`@TestConfiguration` + `@Primary`, default 람다는 throw). 엔드포인트당 시나리오 3~5건, 응답 contract(`status`·`code`·`detail`·`data`)를 단언에 포함. 검증 실패(400)·비즈니스 예외(409 등)도 포함.

**E2E**: `@EnabledIfEnvironmentVariable` 또는 `@Disabled` 중 하나를 **반드시** 둔다. 네이밍 `*E2ETest`.

**금지 (위반 시 컨벤션 어긋남):**

- `@MockBean`·`@SpyBean` 남용, Mockito 로 내부 컴포넌트 모킹 → 내부는 실제 빈으로. 외부 경계만 stub.
- `@DirtiesContext`·`@ActiveProfiles`·`@TestPropertySource` → 컨텍스트 캐시를 깬다.
- `@BeforeEach`·`@BeforeAll` 셋업 hook 으로 fixture·stub 미리 채우기 → 각 테스트 본문에서 직접 만든다. DB 격리는 클래스 레벨 `@Transactional` 자동 롤백.
- 서비스 단독 테스트.

**스타일**: 메서드명은 한국어 식별자로 시나리오 한 문장. 단언은 JUnit5 `Assertions` 기본, 표현력 차이가 큰 경우만 AssertJ — **한 메서드에서 두 스타일을 섞지 않는다**.

> **Spring Boot 4 함정**: `@AutoConfigureMockMvc` 의 패키지가 바뀌었다. `org.springframework.boot.test.autoconfigure.web.servlet` 이 아니라 **`org.springframework.boot.webmvc.test.autoconfigure`** 다. Boot 3 예제를 그대로 붙이면 `cannot find symbol` 이 난다.

### 4단계: 컨벤션·하드코딩 self-audit

변경된 `src/main` 파일을 (리뷰어가 잡기 전에) 훑는다:

- **깊은 `if (x == null)` 중첩 금지** → guard clause + early return. 부재가 의미 있는 반환은 `Optional<T>`, 불변식 강제는 `Objects.requireNonNull`.
- **매직 값 금지** → `static final` 상수·enum. 시간 단위·임계치·캐시 TTL·외부 API base URL·path·param key 전부 해당.
- **분류·상태·타입은 enum**, 가능하면 상수별 메서드로 분기 제거 (`PolicyType`·`TransportMode` 등).
- **캡슐화·rich domain**: 필드 `private`, public setter 금지. 상태 변경·계산은 도메인 메서드로. 서비스에 분기가 쌓이면 도메인으로 내린다.
- **추상화·DIP**: 외부 의존성은 도메인이 port 인터페이스에만 의존. 구현(adapter)은 `infrastructure/` 에 격리.
- **다형성·전략**: 변형되는 행위는 `if/else` 타입 스위치 대신 다형성(인터페이스·enum 전략·`sealed interface` + 패턴 매칭)으로.
- **하드코딩 시크릿·키 금지** → 전부 env·Properties. 환경 무관 고정값만 named 상수.
- **로깅**: Lombok `@Slf4j`, SLF4J `{}` placeholder, API 키·토큰·URL 쿼리스트링·사용자 입력 원본 마스킹, 레벨(info=정상 흐름·클라이언트 계약 위반 / warn=외부 호출 실패·재시도 / error=예상 못한 버그 + 스택).
- **`@Transactional` 은 서비스 메서드 레벨**, 조회 전용은 `readOnly = true`. **외부 호출은 트랜잭션 밖에서** (read-timeout 이 길어 커넥션 풀 고갈). self-invocation 주의.
- **도메인 예외**: "멀쩡한 클라이언트가 정상 요청으로 여기 닿을 수 있나" — 닿으면 커스텀 예외(private 생성자 + static 팩토리, `ErrorCode` 참조), 못 닿으면 `Objects.requireNonNull`·`IllegalStateException`. 예외 message 에 내부 식별자·사용자 입력 원본·외부 API 원문 노출 금지.
- **에러 코드 번호는 append-only** — 재사용·재배치 금지, 결번 유지.
- **DTO ↔ 도메인 매핑은 DTO 자신에** (`from`·`toXxx`), 별도 Mapper 클래스·빈 금지.
- **응답은 `ApiResponseBody` 래퍼**, `ResponseEntity`·raw DTO 직접 노출 금지 (3xx 리다이렉트만 예외), **204 금지**(200 + data=null).
- **Flyway**: 적용된 마이그레이션 수정·삭제 금지(새 timestamp 로 추가), forward-only, MySQL 문법, **FK 제약 추가 금지**(조회 인덱스는 유지).
- **JPA**: 연관관계는 애그리거트 내부만, 경계를 넘으면 raw ID. default LAZY, `@ManyToOne(fetch = EAGER)` 금지. N+1 은 fetch join·`@EntityGraph`·`@BatchSize` 로 차단.

### 값이 나가는 자리를 센다 — 한 곳만 채우고 끝내지 않는다

**새 값을 더했으면 그 값을 만드는 자리와 내보내는 자리를 세어서 적는다.** 팩토리가 다섯이면 다섯을 다 봤는지, 응답이 넷이면 넷에 다 실었는지. 숫자로 답한다.

이걸 안 세면 기능이 **"될 때도 있고 안 될 때도 있는"** 모양으로 나간다. 실제로 #420(교통 시간표) 하나가 후속 이슈 둘을 남겼다.

| 이슈 | 채운 곳 | 빠뜨린 곳 |
|---|---|---|
| #422 | `POST /courses/generate` | `GET /courses/{id}` — **그 카드가 실제로 뜨는 유일한 화면** |
| #427 | `RegionAccess.available` | `noStation` · `noServiceOnDate` · `unavailable` · `pointOnly` |

**빌더가 이 실패를 가능하게 한다.** `@Builder` 로 만드는 타입에 필드를 더하면, 그 필드를 안 채우는 기존 팩토리들이 **조용히 컴파일된다.** `switch` 가 상수를 덮어 컴파일을 깨는 것과 정반대다. 조립에 빌더를 쓰는 것은 우리 규약이 맞지만, 그 대가가 "빠뜨려도 초록" 이라는 것을 알고 세야 한다.

세는 법은 `grep` 한 줄이다. 오래 걸리지 않는다.

```bash
# ① 이 타입을 만드는 자리 전부 (팩토리·빌더 호출)
grep -rn "RegionAccess\.\|RegionAccessBuilder" src/main/java --include=*.java

# ② 이 값을 읽어 내보내는 자리 전부
grep -rn "totalSlots()\|getPoiContentId()" src/main/java --include=*.java
```

**같은 값이라도 자리마다 답이 다를 수 있다.** 전부 채우는 것이 답이 아니라, **자리마다 답을 정하는 것**이 답이다. 교통 거점 칸(#415)은 지도 썸네일 좌표에는 **들어가고**(동선이 실제로 역에서 시작한다) 목록의 "N곳" 에는 **안 들어간다**(들르는 곳이지 고른 장소가 아니다). 뺀 자리는 왜 뺐는지 PR 에 적는다.

기계로 못 막는다 — 정규식이 "팩토리 다섯 중 하나가 빠졌다" 를 못 본다. 그래서 훅이 아니라 여기 있다.

### 작업을 마치기 전 자문 셋 (`CLAUDE.md`)

컨벤션과 별개로, **이 변경이 우리 제약 안에서 사는지**를 셋으로 묻는다. 답을 PR 본문에 남긴다 — "해당 없음" 도 답이다.

1. **운영에서 버티는가** — 부팅 적재·테이블 크기·인덱스가 늘었나. 늘었으면 **빈 DB 최초 부팅과 재부팅을 둘 다 재고** 숫자를 남긴다. 운영 DB 는 EC2 도커 안의 MySQL 하나뿐이라 스케일아웃이 없다.
2. **외부 API 한도를 우리가 갉아먹지 않나** — 배포·부팅·스케줄러가 소비하는 건수를 센다. `fixedDelay` 는 재배포하면 주기가 처음부터 다시 센다. **조사·실측도 같은 한도를 쓴다**(실제로 그렇게 하루 한도를 태워 운영 코스 생성을 degrade 시킨 적이 있다).
3. **코스의 완성도가 올라가는가** — 늘어난 후보 중 **실제로 쓸 수 있는 수**(좌표 없으면 동선에 못 올린다), 카드가 채워지는지(사진·소개·운영시간), 얇은 지역이 채워지는지(89곳 커버리지와 지역당 최소·중앙값). 무관한 내부 개선이면 그렇게 적는다.

발견 시 고치고 3단계 테스트도 갱신한다.

### 5단계: 응답 전수 문서화 체크 (`*Api.java`)

새 엔드포인트·시그니처 변경이 있으면, 해당 `*Api.java` 의 `@ApiResponse` 가 **멀쩡한 클라이언트가 정상 요청으로 받을 수 있는 모든 응답**을 빠짐없이 담는지 확인한다.

조사 대상 다섯 군데:

1. 성공 응답 (컨트롤러 `@ResponseStatus` 와 일치)
2. Spring Security 권한 (비-permitAll → 401, 권한요구 → 403)
3. 도메인 커스텀 예외의 status
4. 외부 의존성 실패 5xx (예: TourAPI 실패 → 502)
5. Bean Validation → 400

- description 은 구체적으로. "잘못된 요청" 금지 → "연차 일수가 음수·형식 오류" 처럼 실제 원인 나열.
- **제외**: 서버 버그·불변식 위반이 만드는 일반 500 (정상 요청으로 도달 불가). framework 4xx(깨진 body·잘못된 메서드·미디어타입)도 "정상 요청" 이 아니라 per-endpoint 문서화 대상이 아니다.

> **현재 springdoc-openapi 의존성이 없다.** `*Api` 인터페이스를 처음 만드는 작업에서 `build.gradle.kts` 에 추가한다 (Spring Boot 4 호환 버전을 Maven Central 에서 확인). 그전까지 이 단계는 해당 없음.

### 6단계: 전역 컴포넌트 변경 시 blast-radius 노트

`GlobalExceptionHandler`·`SecurityConfig`·공통 필터·`ApiResponseBody`·`ErrorCategory` 등 **전역 공유 컴포넌트**를 바꿨다면 영향이 내 엔드포인트 너머로 번진다. 영향받는 컨트롤러·엔드포인트를 추려 **누구의 어떤 엔드포인트가 어떻게 바뀌는지** 를 PR 본문에 적도록 준비한다. "남의 파일은 안 건드렸지만 동작은 바뀐다" 를 명확히.

### 7단계: 테스트 실행

**`./gradlew build` 가 아니라 `./gradlew test`** 를 돌린다.

Gradle 은 JDK 17 이상을 요구한다(빌드 자체는 `build.gradle.kts` 의 toolchain 을 따른다). `JAVA_HOME` 이 그보다 낮은 JDK 를 가리키면 `Gradle requires JVM 17 or later` 로 죽는다.

**머신별 경로를 문서에 박지 않는다.** 아래는 현재 `JAVA_HOME` 을 검사하고, 낮으면 설치된 JDK 중 조건을 만족하는 것을 찾아 그 호출에서만 쓴다.

```bash
MIN=17
jdk_major() {  # $1 = JAVA_HOME 후보 → major 버전 출력 (판정 불가면 0)
  [ -x "$1/bin/java" ] || return 1
  "$1/bin/java" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1
}

CUR=$(jdk_major "${JAVA_HOME:-}" 2>/dev/null || echo 0)
if [ "${CUR:-0}" -lt "$MIN" ]; then
  # PATH 의 java 가 조건을 만족하면 그 홈을 쓴는다 (JAVA_HOME 만 낡은 흔한 경우)
  PATH_JAVA=$(command -v java 2>/dev/null) \
    && CAND=$(cd "$(dirname "$(readlink -f "$PATH_JAVA" 2>/dev/null || echo "$PATH_JAVA")")/.." && pwd) \
    && [ "$(jdk_major "$CAND" 2>/dev/null || echo 0)" -ge "$MIN" ] \
    && export JAVA_HOME="$CAND"
fi

FOUND=$(jdk_major "${JAVA_HOME:-}" 2>/dev/null || echo 0)
if [ "${FOUND:-0}" -lt "$MIN" ]; then
  echo "JDK $MIN 이상을 찾지 못했습니다 (현재 JAVA_HOME=${JAVA_HOME:-미설정}). 설치 후 JAVA_HOME 을 잡아주세요." >&2
  exit 1
fi
echo "JAVA_HOME=$JAVA_HOME (JDK $FOUND)"
./gradlew test --console=plain
```

> 이 머신처럼 `java -version` 은 최신인데 `JAVA_HOME` 만 옛 JDK 를 가리키는 경우가 흔하다. 위 스크립트는 그 상황을 자동으로 넘기지만, **근본 해결은 `JAVA_HOME` 을 영구 수정하는 것**이다.

- **실패** → 실패 테스트·오류를 출력하고 **즉시 중단**. PR 만들지 않는다. (수정 후 `/pre-pr` 재실행)
- **성공** → 아래 형식으로 결과 출력:

```
## 테스트 결과
| 항목 | 결과 |
|---|---|
| 전체 | N passed |
| 신규 작성 | XxxTest, ... (없으면 "없음") |
```

건수 집계가 필요하면 `build/test-results/test/*.xml` 의 `testsuite` 속성을 합산한다.

> **도커가 필요하다** — 통합 테스트가 Testcontainers 로 MySQL 을 띄운다(#175). 운영과 같은 DB 로 검증하려는 것이고, H2 시절에는 못 잡던 마이그레이션 문법 오류가 로컬에서 바로 걸린다.

### 8단계: 테스트 커밋

테스트를 새로 썼으면 `/commit` 으로 커밋한다 (타입 `test:`, 테스트 파일만). 본 구현이 아직 미커밋이면 그 커밋도 `/commit` 규칙에 따라 분리 커밋.

### 9단계: PR 생성

`/pr` 을 호출한다. `/pr` 은 대화 컨텍스트로 본문을 쓰므로 위 테스트 결과·self-audit·blast-radius 노트가 자연스럽게 PR 본문에 반영된다.

## 한 줄 요약

`origin/dev` 기준 + up-to-date → 규약 테스트(단위·통합, 내부 모킹 금지) → 컨벤션·하드코딩 self-audit → **값이 나가는 자리 전수** → 응답 전수 문서화 → (전역이면) blast-radius → `./gradlew test`(build 아님) → `/commit` → `/pr`.

$ARGUMENTS

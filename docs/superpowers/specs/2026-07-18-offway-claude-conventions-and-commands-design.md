# OffWay — 개발 컨벤션 · Claude 커맨드 세트 설계

- 작성일: 2026-07-18
- 상태: 확정 (2026-07-18 사용자 리뷰 완료)
- 참고 소스: `~/Desktop/git/core` (PIKI 백엔드, Kotlin, `com.depromeet.piki`)

## 1. 목표

OffWay `core` 백엔드(Spring Boot 4.1 / Java 25) 개발을 시작하기 전에, **Claude가 따를 개발 컨벤션과 반복 작업 커맨드를 미리 갖춘다.** PIKI `core`의 검증된 컨벤션·커맨드를 **차용하되 (a) Java로 번역하고 (b) 2인·단발성 MVP에 맞게 인프라 자동화를 걷어낸다.**

핵심 원칙: **코드 컨벤션·커맨드 UX는 풀 이식 / 인프라 자동화는 경량.**

## 2. 산출물

| 산출물 | 위치 | 비고 |
|---|---|---|
| 컨벤션 문서 | `CLAUDE.md` | core CLAUDE.md의 Java 번역·경량화 |
| 테스트 컨벤션 | `.claude/rules/testing-convention.md` | JUnit5 기준 |
| 공통 기반 코드 | `src/main/java/com/offway/core/common/` | 응답 래퍼·예외·에러코드·전역 핸들러 |
| 커맨드 8종 | `.claude/commands/*.md` | commit · pr · issue · coderabbit · branch · api-client · migration · entity |
| 이슈/PR 템플릿 | `.github/ISSUE_TEMPLATE/*`, `.github/PULL_REQUEST_TEMPLATE.md` | 경량 이식 |
| CodeRabbit 설정 | `.coderabbit.yml` | 경량 |
| 경량 Project 보드 | GitHub Project (v2) | 커맨드가 이슈/PR을 보드에 추가 (자동연동 워크플로우는 없음) |

## 3. 아키텍처 — package-by-feature

`com.offway.core` 하위를 도메인별로 나눈다. 각 도메인은 내부에 `controller / service / domain / repository / dto / exception`을 둔다 (core와 동일 철학, 필요한 하위만).

```
com.offway.core
├── leave        # 연차·가용시간(LNT)·샌드위치 연휴·연차 컨설팅 (순수 도메인 로직 — 테스트 핵심)
├── trip         # 인구감소지역·관광지 추천 (TourAPI)
├── policy       # 7대 여행 지원 혜택 매칭 (수동 적재 데이터)
├── transport    # 교통/동선 (TAGO·TMAP·코레일/SR)
├── itinerary    # 일정표 자동 생성 (leave+trip+transport 조합)
├── external     # 외부 API 클라이언트 격리 (WebClient) ← /api-client 생성 위치
├── user         # 사용자·인증 (Spring Security + OAuth2)
└── common       # 응답 래퍼·BaseException·ErrorCategory·GlobalExceptionHandler·config
```

- `external`는 외부 API별 서브패키지(`external/tour`, `external/tago`, `external/tmap`, `external/holiday` …)로 클라이언트를 격리한다. 도메인 서비스는 `external`의 인터페이스에만 의존한다.
- 도메인 간 참조는 **raw ID + 서비스 계층 조회**로 한다 (§4.5 FK 정책과 일관).

## 4. 컨벤션 (CLAUDE.md 내용) — core → Java 25 번역

core의 컨벤션을 **의미는 유지하고 Java 관용구로 번역**한다. Java 25의 `record` · `sealed` · pattern matching · Lombok을 활용한다.

### 4.1 Null 처리
- 깊은 `if (x == null)` 중첩 금지. **guard clause + early return**으로 푼다.
- 부재가 의미 있는 반환값은 `Optional<T>`. 불변식 강제는 `Objects.requireNonNull(x, "...")`.
- Kotlin Elvis(`?:`) 대응은 `Optional.orElse / orElseThrow` 또는 early-return 가드.

### 4.2 도메인 예외 정책 — 불변식 vs 계약
판단 한 줄: **"멀쩡한 클라이언트가 정상 요청으로 여기 닿을 수 있나?"**
- 닿는다 → **계약** → 커스텀 예외 (400/409/403/502 등)
- 못 닿는다 → **불변식** → `Objects.requireNonNull` / `if (!cond) throw new IllegalStateException(...)` (500, 코드 버그 신호)
- 도메인이 자기방어: 도메인 메서드가 직접 커스텀 예외를 던진다.
- 한 메서드에 불변식 검증과 계약 예외가 공존해도 정상.

### 4.3 커스텀 예외 형태 (Java)
- `{도메인명사}Exception`(예: `LeaveException`, `PolicyException`, `TourApiException`).
- `BaseException`(RuntimeException 상속) + `HttpMappable`(interface: `ErrorCategory getCategory()`) 패턴.
- **private 생성자 + static 팩토리 메서드**. 각 팩토리는 사유 하나 = message + `ErrorCode` 한 곳에 고정.
  ```java
  public final class LeaveException extends BaseException implements HttpMappable {
      private LeaveException(LeaveErrorCode code) { super(code.getMessage()); this.code = code; }
      public static LeaveException invalidAnnualLeave() { return new LeaveException(LeaveErrorCode.INVALID_ANNUAL_LEAVE); }
      // getCategory()는 code.getCategory()에서 파생
  }
  ```
- **message는 전부 사용자 대면 고정 문구.** 내부 식별자·입력 원본·기술 용어 금지 → 디버깅 정보는 로그·cause로.

### 4.4 에러 코드
- 도메인별 `*ErrorCode` enum이 `code`(예: `LEAVE-001`) · `category` · `message`를 single source로 보유.
- **번호 append-only** (재사용·재배치 금지).
- status는 `ErrorCategory` → `HttpStatus` 1:1 매핑이 소유. 성공 응답은 `code = null`.

### 4.5 외래 키 · JPA 연관관계 — 경계 기준 절충
core의 "전면 금지"는 대규모·다인 협업용이라 OffWay(1인·MVP·얕은 관계)엔 과하다. **DB FK는 끄되, JPA 연관관계는 애그리거트 경계 기준으로 허용**하는 절충안을 택한다.

**(a) DB `FOREIGN KEY` 제약 — 두지 않음 (core 유지)**
- 마이그레이션에 `CONSTRAINT ... FOREIGN KEY` 추가 금지. 조회 인덱스(`KEY idx_*`)는 유지.
- 이유: 채택한 Flyway **additive·out-of-order·forward-only** 규칙(§4.6)과 FK가 상충(순서 의존·테스트 데이터 복잡). 참조 무결성은 서비스 계층이 책임.
- (JPA 연관관계를 쓰더라도 `@JoinColumn(foreignKey = @ForeignKey(NO_CONSTRAINT))`로 DDL FK를 끈다.)

**(b) JPA 연관관계 — 애그리거트 내부만 허용**

| 상황 | 정책 |
|---|---|
| **애그리거트 내부** (예: `Itinerary` ↔ `ItineraryItem` — 생명주기 공유, 항상 같이 로드/저장) | `@OneToMany`/`@ManyToOne` **허용** |
| **애그리거트/도메인 경계 넘음** (예: `Itinerary`→`User`, `Item`→`Region`/`Attraction`, `Policy`→`Region`) | **raw ID 필드만** (`Long userId` 등). 연관관계 어노테이션 미사용 |

- 실무상 OffWay의 도메인 간 참조 대부분(지역·여행지·정책)은 외부 API에서 온 레퍼런스 데이터라 온전한 JPA 엔티티가 아닐 수 있고, 자연히 raw ID가 된다. `@ManyToOne`은 진짜 한 덩어리인 소수에만 쓴다.

**(c) N+1 가드 (연관관계를 쓰는 대가)**
- **default `LAZY`. `@ManyToOne(fetch = EAGER)` 금지.**
- 컬렉션 조회 시 N+1은 **fetch join / `@EntityGraph` / `@BatchSize`(또는 `default_batch_fetch_size`)** 로 차단한다.

### 4.6 DB 마이그레이션 (Flyway)
- 위치 `src/main/resources/db/migration/`. 네이밍 `V{YYYYMMDDHHmmss}__{snake_case}.sql` (KST, `date +%Y%m%d%H%M%S`).
- **적용된 파일 수정·삭제 금지** (checksum). 변경은 새 timestamp 마이그레이션 추가.
- **forward-only** (down 마이그레이션 없음). **additive·commutative** 유지 (`out-of-order: true`).
- FK 금지. DROP/RENAME 등 destructive는 add→backfill→drop 단계 배포.
- **MySQL/H2 양쪽 호환** SQL 작성 (운영 MySQL, 로컬 H2 — H2는 MySQL 호환 모드). `/migration`이 생성.

### 4.7 트랜잭션 경계 (외부 API 다수라 특히 중요)
- `@Transactional`은 **서비스 메서드 레벨**. 조회 전용은 `@Transactional(readOnly = true)`.
- **외부 호출(TourAPI·TAGO·TMAP 등)은 트랜잭션 밖에서.** read-timeout이 길어 DB 커넥션을 잡으면 풀이 고갈된다.
- 외부 호출을 끝낸 뒤 **영속화만 별도 빈(`@Transactional`)에 위임**.
- self-invocation 주의(같은 빈 내 `@Transactional` 직접 호출은 프록시 우회 → 무력화). 경계는 별도 빈으로 추출.

### 4.8 로깅
- Lombok `@Slf4j` 사용 (core의 명시 선언 대신 Java+Lombok 관용구).
- 민감 정보(URL 쿼리스트링·토큰·API 키·사용자 입력 원본) 마스킹. 외부 API 키는 절대 로그 금지.
- 레벨: info(정상·클라 계약 위반) / warn(외부 호출 실패·재시도) / error(예상 못한 서버 버그, 스택 포함).
- SLF4J `{}` placeholder 사용.

### 4.9 DTO ↔ 도메인 매핑
- **매핑은 DTO 자신에.** 별도 Mapper 클래스 금지.
- 응답 DTO(record): static `from(도메인)`. 요청 DTO: 인스턴스 `toXxx()`. 외부 응답 → 도메인: `toXxx()`.

### 4.10 컨트롤러 / OpenAPI
- 컨트롤러는 `*Api` 인터페이스 구현. 인터페이스에 OpenAPI 어노테이션(`@Tag`/`@Operation`/`@ApiResponse`), 구현체에 매핑·검증 어노테이션.
- **응답 전수 문서화**: 성공 + 도달 가능한 모든 실패(4xx 계약, 502 외부의존성)를 `@ApiResponse`로 선언. 일반 500(불변식·버그)은 제외.
- ⚠️ **의존성 추가 필요**: `springdoc-openapi-starter-webmvc-ui`를 `build.gradle.kts`에 추가 (Maven Central 최신 안정, Spring Boot 4 호환 확인).
- ⚠️ **리뷰 포인트**: 이 규칙(특히 example 객체화·fail detail single source)은 가장 무거운 컨벤션. MVP 초기엔 "`@ApiResponse` 선언까지만, example 객체화는 후속"으로 단계 적용할지 스펙 리뷰에서 결정. (기본값: 선언 전수화는 채택, example 객체화는 **후속 여지**.)

### 4.11 응답 포맷 — 공통 래퍼 (풀 채택)
- 모든 응답을 `ApiResponseBody<T>`로 감싼다 (record). 필드: `int status`, `T data`, `String detail`, `String code`, `PageResponse pageResponse`.
  ```jsonc
  { "status": 200, "data": {...|null}, "detail": "...", "code": "OK|LEAVE-001|...", "pageResponse": {...|null} }
  ```
- static 팩토리 `ok(...)` / `created(...)` / `fail(...)`. 실패는 `GlobalExceptionHandler`(`@RestControllerAdvice`)가 `fail`로 매핑.
- 3xx 리다이렉트는 래퍼 예외(`ResponseEntity<Void>`). **204 미사용** (200 + data=null).
- piki-web-auth 스타일과 계약 일관 (프론트 1명이 이미 아는 포맷).

### 4.12 도메인 용어 (OffWay glossary)
- **LNT** — 총 가용 시간(Leave-based Net Time). 연차+공휴일+주말로 산출한 실제 여행 가능 시간.
- **샌드위치 연휴** — 공휴일·주말 사이 평일에 연차를 끼워 최소 연차로 최대 휴식.
- **인구감소지역** — 행안부 고시 89곳. 추천 대상 지역.
- **7대 혜택** — 숙박세일페스타·디지털관광주민증·근로자휴가지원·지자체바우처·KTX/SRT할인·로컬100/관광두레·농촌체험/치유관광.
- (커밋·PR·코드에서 이 용어를 일관되게 사용.)

### 4.13 의존성 관리
- 버전 단일 진실 원천은 `build.gradle.kts`. 문서에 버전 숫자 박지 않음.
- 새 의존성은 Maven Central 최신 안정 조회 후 추가 (pre-release 제외). Spring Boot BOM 관리 대상은 버전 명시 안 함.

### 4.14 YAGNI / 가까운 미래
- 가설적 먼 미래를 위한 추상화는 만들지 않음. 단, 합의된 가까운 후속(예: Nice-to-have 기능)과 충돌하지 않게 설계.

## 5. 공통 기반 코드 (`common` 패키지)

컨벤션과 커맨드가 참조하므로 **먼저 만든다.** (없으면 `/entity`·`/api-client`가 존재하지 않는 클래스를 참조하는 코드를 생성.)

- `ApiResponseBody<T>` (record + 팩토리) · `PageResponse`
- `BaseException`(RuntimeException) · `HttpMappable`(interface)
- `ErrorCategory`(enum → HttpStatus) · `ErrorCode`(interface, 도메인별 enum이 구현)
- `GlobalExceptionHandler`(`@RestControllerAdvice`: BaseException·BeanValidation·Security·일반 500 매핑)

## 6. 커맨드 8종

core 커맨드를 참고하되 **인프라 결합(dev/staging·Discord·Project 자동연동·worktree 강제) 제거**, 경량화.

| 커맨드 | 하는 일 | core 대비 |
|---|---|---|
| `/commit` | staged 분석 → 한글 Conventional Commit(`type: 설명`) 생성. 본문은 맥락 위주 | 신규(경량) |
| `/branch` | `main`에서 컨벤션 브랜치 생성(`feat/leave-sandwich` 등), 이슈번호 연동 가능 | 신규(경량) |
| `/issue` | 템플릿 기반 이슈 생성 + 라벨 + **경량 Project 보드 추가** | core `issue.md` 경량화 |
| `/pr` | 현재 브랜치 → `main` STAR 구조 PR 생성/갱신. assignee(@me)·라벨·**Project 보드 추가** | core `pr.md`(46KB) 대폭 경량화 (멀티환경·Discord·worktree 가드 제거) |
| `/coderabbit` | 현재 PR의 CodeRabbit 리뷰 코멘트 수집 → 분류 → 반영/답변 | 신규 |
| `/api-client` | `external/{api}`에 WebClient 클라이언트 스캐폴딩: config·DTO(record)·에러(→502 커스텀 예외)·선택적 Redis 캐싱. tx 밖 호출 규칙 준수 | 신규 (OffWay 핵심) |
| `/migration` | Flyway 마이그레이션 파일 생성(`V{timestamp}__desc.sql`, MySQL/H2 호환, FK 금지, additive) | 신규 |
| `/entity` | JPA 엔티티(경계 넘는 참조는 raw ID·애그리거트 내부만 연관관계·LAZY·생성자 불변식 검증) + Repository 스캐폴딩 | 신규 |

- 커밋 컨벤션: Conventional Commits 타입 접두어(`feat`/`fix`/`chore`/`refactor`/`docs`/`test`) + **한글 설명**. (초기 커밋 `chore: 프로젝트 초기화`와 일관.)
- PR STAR 구조: Situation / Task / Action / Result. 대화 맥락·트레이드오프·결정 이유를 담되 표·굵은 불릿으로 구조화.

## 7. .github 템플릿 · Project 보드 · CodeRabbit

- **이슈 템플릿(경량)**: `task.yml`(작업) · `bug.yml`(버그) · `feature.yml`(기능). core의 epic/refactor는 생략(2인 MVP). `config.yml`로 blank 이슈 정리.
- **PR 템플릿**: `PULL_REQUEST_TEMPLATE.md` — STAR 구조 골격.
- **경량 Project 보드**: GitHub Project(v2) 1개, 상태 컬럼(Todo/In Progress/Done) 정도. **자동연동 워크플로우는 만들지 않고**, `/issue`·`/pr` 커맨드가 `gh project item-add`로 직접 추가. (core의 issue-project-sync·pr-merge-sync 등 워크플로우 이식 안 함.)
- **`.coderabbit.yml`(경량)**: 리뷰 언어(한국어)·경로 필터·핵심 규칙만. core의 12KB 설정을 그대로 쓰지 않고 최소본으로.
- **제거 대상(이식 안 함)**: Discord 워크플로우, dev/staging/prod promote·deploy, epic-tracker, auto-reviewer, 모니터링/메트릭 스택.

## 8. 제작 순서

**컨벤션·기반 먼저 → 커맨드.** (커맨드가 컨벤션·기반 코드를 참조.)

1. `CLAUDE.md`(컨벤션) + `.claude/rules/testing-convention.md`
2. 공통 기반 코드(`common` 패키지) + `build.gradle.kts` 의존성 추가(springdoc 등) + `application.properties` 기본 설정
3. 패키지 골격(빈 도메인 패키지 8개)
4. `.github` 템플릿 + 경량 Project 보드 + `.coderabbit.yml`
5. Git 커맨드: `/commit` · `/branch` · `/issue` · `/pr` · `/coderabbit`
6. 코드 스캐폴딩 커맨드: `/api-client` · `/migration` · `/entity`

## 9. Out of Scope

- Discord 알림/봇, 멀티환경(dev/staging/prod) 배포 파이프라인, GitHub Project 자동연동 워크플로우
- 모니터링/메트릭 스택, worktree 강제 워크플로우
- 실제 도메인 기능 구현(연차 계산·추천 알고리즘 등) — 본 스펙은 **컨벤션·도구 셋업**까지. 기능은 이후 별도 스펙.
- 외부 API 발급처·엔드포인트 상세 조사 — 별도 리서치 작업으로 분리(추후).

## 10. 열린 결정 (스펙 리뷰에서 확정)

1. ~~FK/JPA 연관관계 정책~~ → **확정**: DB FK OFF + 애그리거트 내부만 연관관계 허용 + 경계는 raw ID + N+1은 LAZY/fetch join 가드 (§4.5).
2. **OpenAPI 전수 문서화 강도** (§4.10): example 객체화까지 초기 적용 vs 후속? (기본값: 선언 전수화 채택, example 객체화 후속 여지)
3. 이슈 템플릿에 epic/refactor 포함 여부 (기본값: 미포함)

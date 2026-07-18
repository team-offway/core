# 영속성 규약 — 연관관계 · 마이그레이션 · 트랜잭션

`CLAUDE.md` 가 import 한다. JPA 연관관계, Flyway 마이그레이션, 트랜잭션 경계를 담는다.

## 외래 키 · JPA 연관관계 — 경계 기준

### (a) DB `FOREIGN KEY` 제약 — 두지 않는다

- 마이그레이션에 `CONSTRAINT ... FOREIGN KEY` 를 추가하지 않는다. 조회 인덱스(`KEY idx_*`)는 유지한다.
- 이유: 아래 Flyway **additive·out-of-order·forward-only** 규칙과 FK 가 상충한다(적용 순서 의존·테스트 데이터 복잡). 참조 무결성은 서비스 계층이 책임진다.
- JPA 연관관계를 쓰더라도 DDL FK 는 끈다: `@JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))`.

### (b) JPA 연관관계 — 애그리거트 내부만 허용

| 상황 | 정책 |
|---|---|
| **애그리거트 내부** (생명주기 공유, 항상 같이 로드/저장 — 예: `Itinerary` ↔ `ItineraryItem`) | `@OneToMany`/`@ManyToOne` **허용** |
| **애그리거트/도메인 경계 넘음** (예: `Itinerary`→`User`, `Item`→`Region`, `Policy`→`Region`) | **raw ID 필드만** (`Long userId` 등). 연관관계 어노테이션 미사용 |

- OffWay 의 도메인 간 참조 대부분(지역·여행지·정책)은 외부 API 에서 온 레퍼런스 데이터라 온전한 JPA 엔티티가 아닐 수 있고, 자연히 raw ID 가 된다. `@ManyToOne` 은 진짜 한 덩어리인 소수에만 쓴다.

### (c) N+1 가드 (연관관계를 쓰는 대가)

- **default `LAZY`. `@ManyToOne(fetch = EAGER)` 금지.**
- 컬렉션 조회 시 N+1 은 **fetch join / `@EntityGraph` / `@BatchSize`**(또는 `hibernate.default_batch_fetch_size`)로 차단한다.

## 엔티티 작성

- 필드 `private`, public setter 금지(캡슐화). 생성은 static 팩토리 / 빌더로.
- **생성자·팩토리에서 불변식 검증**(`require`) — 엔티티는 누가 만들든 스스로 유효함을 보장하는 최후의 보루다.
- 식별자 전략은 팀 표준(`@Id @GeneratedValue(strategy = IDENTITY)` 등)을 따른다.

## DB 마이그레이션 (Flyway)

- **위치**: `src/main/resources/db/migration/`.
- **네이밍**: `V{YYYYMMDDHHmmss}__{snake_case_description}.sql` (KST, 파일 만들 때 현 시각 `date +%Y%m%d%H%M%S`).
- **MySQL / H2 양쪽 호환 SQL** 로 작성한다. 운영은 MySQL, 로컬은 H2(`MODE=MySQL`)로 같은 마이그레이션이 돌아야 한다(로컬 실행성 불변식).

### 규칙

- **적용된 마이그레이션 파일은 수정·삭제하지 않는다** (Flyway checksum). 변경이 필요하면 **새 timestamp 로 추가 마이그레이션**을 만든다.
- **forward-only.** down 마이그레이션·롤백 SQL 을 쓰지 않는다. 잘못된 마이그레이션은 새 보정 마이그레이션으로 되돌린다.
- **additive·commutative 유지 (`out-of-order: true`).** `ADD COLUMN`·`CREATE INDEX`·`CREATE TABLE` 처럼 순서 무관한 변경을 기본으로 한다.
- **순서 의존 변경**(컬럼 rename, 기존 컬럼 `NOT NULL` 화, 데이터 backfill 등)과 **DROP/RENAME 류 destructive** 작업은 **add → backfill → drop** 3단계로 나눠 배포한다.
- **FK 제약 추가 금지** (위 참조). 조회 인덱스는 유지.
- 변경 의도가 한눈에 드러나는 description 을 쓴다.

## 트랜잭션 경계

- **`@Transactional` 은 서비스 메서드 레벨.** 조회 전용은 `@Transactional(readOnly = true)`.
- **외부 호출은 트랜잭션 밖에서.** TourAPI·TAGO·TMAP·특일정보 등 외부 호출은 read-timeout 이 길어 트랜잭션 안에 넣으면 DB 커넥션을 오래 잡아 풀이 고갈된다.
  - 외부 호출을 트랜잭션 밖에서 끝낸 뒤, **영속화만 별도 빈(`@Transactional`)에 위임**해 짧은 트랜잭션으로 묶는다.
- **self-invocation 주의.** 같은 빈 안에서 `@Transactional` 메서드를 직접 호출하면 Spring AOP proxy 를 거치지 않아 트랜잭션이 무력화된다. 경계를 분리하려면 별도 빈으로 추출한다.

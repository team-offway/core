# OffWay 프로젝트 설정 · 공통 기반 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OffWay `core` 백엔드가 시크릿 없이 로컬(H2)에서 항상 부팅되고, 공통 응답 래퍼·예외 체계·컨벤션 문서를 갖춘 상태로 만든다.

**Architecture:** package-by-feature(`com.offway.core.<domain>`) 골격을 세우고, `common` 패키지에 응답 래퍼(`ApiResponseBody`)·예외 primitive(`BaseException`/`ErrorCode`/`ErrorCategory`)·전역 핸들러(`GlobalExceptionHandler`)를 둔다. `local` 프로파일은 H2 인메모리로 외부 인프라·시크릿 0에 부팅한다.

**Tech Stack:** Spring Boot 4.1 · Java 25 · Lombok · JPA · Flyway · H2(local)/MySQL(prod) · JUnit5

참고 스펙: `docs/superpowers/specs/2026-07-18-offway-claude-conventions-and-commands-design.md`

---

## 파일 구조

- `build.gradle.kts` (수정) — `spring-boot-starter-validation` 추가
- `src/main/resources/application.properties` (수정) — 기본 프로파일 `local`
- `src/main/resources/application-local.properties` (생성) — H2 인메모리
- `src/main/resources/application-prod.properties` (생성) — MySQL(환경변수 override)
- `src/main/resources/db/migration/.gitkeep` (생성) — Flyway 위치 확보
- `src/main/java/com/offway/core/common/response/ApiResponseBody.java` · `PageResponse.java`
- `src/main/java/com/offway/core/common/exception/{ErrorCategory,ErrorCode,HttpMappable,BaseException,GlobalExceptionHandler}.java`
- `src/main/java/com/offway/core/{leave,trip,policy,transport,itinerary,external,user}/package-info.java`
- `CLAUDE.md` (생성) · `.claude/rules/testing-convention.md` (생성)
- 테스트: `src/test/java/com/offway/core/common/**`

---

## Task 1: 로컬 실행 baseline (H2 `local` 프로파일)

**Files:**
- Modify: `build.gradle.kts` (dependencies 블록)
- Create: `src/main/resources/application-local.properties`
- Create: `src/main/resources/application-prod.properties`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/db/migration/.gitkeep`
- Test: 기존 `src/test/java/com/offway/core/CoreApplicationTests.java` (contextLoads)

- [ ] **Step 1: validation 의존성 추가**

`build.gradle.kts`의 `dependencies { }`에 한 줄 추가 (Bean Validation — `GlobalExceptionHandler`가 `MethodArgumentNotValidException`을 다룸):

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-validation")
```

- [ ] **Step 2: 프로파일 설정 파일 작성**

`src/main/resources/application.properties` (기본 = local, 운영은 env로 override):

```properties
spring.application.name=offway-core
spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}
spring.jpa.open-in-view=false
```

`src/main/resources/application-local.properties` (H2 인메모리 — 시크릿·외부 인프라 0 부팅):

```properties
# H2 인메모리 (MySQL 호환 모드) — 시크릿 없이 부팅
spring.datasource.url=jdbc:h2:mem:offway;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true

# JPA — 스키마는 Flyway가 만든다
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.default_batch_fetch_size=100

# Flyway (마이그레이션 없어도 no-op으로 부팅)
spring.flyway.enabled=true
spring.flyway.out-of-order=true
spring.flyway.baseline-on-migrate=true

# Redis 로컬 미가동 대비 — 자동설정만 하고 부팅 시 연결하지 않음
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

`src/main/resources/application-prod.properties` (운영 MySQL — 값은 전부 환경변수):

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST}:${DB_PORT:3306}/${DB_NAME}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.default_batch_fetch_size=100
spring.flyway.enabled=true
spring.flyway.out-of-order=true
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.port=${REDIS_PORT:6379}
```

- [ ] **Step 3: Flyway 위치 확보**

빈 마이그레이션 디렉토리를 만들어 Flyway 위치 누락 경고를 없앤다:

```bash
mkdir -p src/main/resources/db/migration
touch src/main/resources/db/migration/.gitkeep
```

- [ ] **Step 4: 로컬 부팅 스모크 — contextLoads 실행**

Run: `./gradlew test --tests "com.offway.core.CoreApplicationTests"`
Expected: PASS (기본 `local` 프로파일로 H2 컨텍스트가 시크릿 없이 뜬다)

> 만약 Redis 자동설정이 부팅을 막으면(연결 시도), `application-local.properties`에 `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration` 를 추가한다.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts src/main/resources/
git commit -m "chore: local 프로파일(H2) 부팅 baseline"
```

---

## Task 2: 예외 primitive (`ErrorCategory`·`ErrorCode`·`HttpMappable`·`BaseException`)

**Files:**
- Create: `src/main/java/com/offway/core/common/exception/ErrorCategory.java`
- Create: `src/main/java/com/offway/core/common/exception/ErrorCode.java`
- Create: `src/main/java/com/offway/core/common/exception/HttpMappable.java`
- Create: `src/main/java/com/offway/core/common/exception/BaseException.java`
- Test: `src/test/java/com/offway/core/common/exception/BaseExceptionTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/offway/core/common/exception/BaseExceptionTest.java`:

```java
package com.offway.core.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BaseExceptionTest {

    enum SampleErrorCode implements ErrorCode {
        NOT_FOUND_SAMPLE("SAMPLE-001", ErrorCategory.NOT_FOUND, "샘플을 찾을 수 없습니다.");

        private final String code;
        private final ErrorCategory category;
        private final String message;

        SampleErrorCode(String code, ErrorCategory category, String message) {
            this.code = code;
            this.category = category;
            this.message = message;
        }

        @Override public String code() { return code; }
        @Override public ErrorCategory category() { return category; }
        @Override public String message() { return message; }
    }

    static final class SampleException extends BaseException {
        private SampleException(ErrorCode errorCode) { super(errorCode); }
        static SampleException notFound() { return new SampleException(SampleErrorCode.NOT_FOUND_SAMPLE); }
    }

    @Test
    void 커스텀_예외는_ErrorCode의_message와_code와_status를_노출한다() {
        SampleException e = SampleException.notFound();

        assertEquals("샘플을 찾을 수 없습니다.", e.getMessage());
        assertEquals("SAMPLE-001", e.errorCode().code());
        assertEquals(HttpStatus.NOT_FOUND, e.errorCode().category().httpStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.offway.core.common.exception.BaseExceptionTest"`
Expected: FAIL — `ErrorCategory` / `ErrorCode` / `BaseException` 심볼이 없어 컴파일 에러

- [ ] **Step 3: Write minimal implementation**

`ErrorCategory.java`:

```java
package com.offway.core.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCategory {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    EXTERNAL_API(HttpStatus.BAD_GATEWAY),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCategory(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
```

`ErrorCode.java`:

```java
package com.offway.core.common.exception;

// 도메인별 *ErrorCode enum이 구현한다. code는 append-only, category→HttpStatus 파생.
public interface ErrorCode {
    String code();          // 예: "LEAVE-001"
    ErrorCategory category();
    String message();       // 사용자 대면 고정 문구
}
```

`HttpMappable.java`:

```java
package com.offway.core.common.exception;

public interface HttpMappable {
    ErrorCode errorCode();
}
```

`BaseException.java`:

```java
package com.offway.core.common.exception;

public abstract class BaseException extends RuntimeException implements HttpMappable {

    private final transient ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    @Override
    public ErrorCode errorCode() {
        return errorCode;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.offway.core.common.exception.BaseExceptionTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/offway/core/common/exception/ src/test/java/com/offway/core/common/exception/BaseExceptionTest.java
git commit -m "feat: 공통 예외 primitive (ErrorCategory·ErrorCode·BaseException)"
```

---

## Task 3: 공통 응답 래퍼 (`ApiResponseBody`·`PageResponse`)

**Files:**
- Create: `src/main/java/com/offway/core/common/response/PageResponse.java`
- Create: `src/main/java/com/offway/core/common/response/ApiResponseBody.java`
- Test: `src/test/java/com/offway/core/common/response/ApiResponseBodyTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/offway/core/common/response/ApiResponseBodyTest.java`:

```java
package com.offway.core.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseBodyTest {

    enum TestCode implements ErrorCode {
        CONFLICT_SAMPLE("SAMPLE-409", ErrorCategory.CONFLICT, "이미 존재합니다.");
        private final String code;
        private final ErrorCategory category;
        private final String message;
        TestCode(String c, ErrorCategory cat, String m) { this.code = c; this.category = cat; this.message = m; }
        @Override public String code() { return code; }
        @Override public ErrorCategory category() { return category; }
        @Override public String message() { return message; }
    }

    @Test
    void ok는_status_200과_OK_코드를_가진다() {
        ApiResponseBody<String> body = ApiResponseBody.ok("hello");
        assertEquals(200, body.status());
        assertEquals("hello", body.data());
        assertEquals("OK", body.code());
        assertNull(body.pageResponse());
    }

    @Test
    void created는_status_201이다() {
        ApiResponseBody<String> body = ApiResponseBody.created("x");
        assertEquals(201, body.status());
        assertEquals("OK", body.code());
    }

    @Test
    void fail은_ErrorCode의_status_code_detail을_반영한다() {
        ApiResponseBody<Void> body = ApiResponseBody.fail(TestCode.CONFLICT_SAMPLE);
        assertEquals(409, body.status());
        assertEquals("SAMPLE-409", body.code());
        assertEquals("이미 존재합니다.", body.detail());
        assertNull(body.data());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.offway.core.common.response.ApiResponseBodyTest"`
Expected: FAIL — `ApiResponseBody` / `PageResponse` 없음

- [ ] **Step 3: Write minimal implementation**

`PageResponse.java`:

```java
package com.offway.core.common.response;

public record PageResponse(String nextCursor, boolean hasNext) {
    public static PageResponse none() {
        return new PageResponse(null, false);
    }
}
```

`ApiResponseBody.java`:

```java
package com.offway.core.common.response;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

public record ApiResponseBody<T>(int status, T data, String detail, String code, PageResponse pageResponse) {

    private static final String OK_CODE = "OK";
    private static final String OK_DETAIL = "정상적으로 처리되었습니다.";

    public static <T> ApiResponseBody<T> ok(T data) {
        return new ApiResponseBody<>(200, data, OK_DETAIL, OK_CODE, null);
    }

    public static <T> ApiResponseBody<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponseBody<T> created(T data) {
        return new ApiResponseBody<>(201, data, OK_DETAIL, OK_CODE, null);
    }

    public static <T> ApiResponseBody<T> fail(ErrorCode errorCode) {
        return new ApiResponseBody<>(
                errorCode.category().httpStatus().value(), null, errorCode.message(), errorCode.code(), null);
    }

    public static <T> ApiResponseBody<T> fail(ErrorCategory category, String detail, String code) {
        return new ApiResponseBody<>(category.httpStatus().value(), null, detail, code, null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.offway.core.common.response.ApiResponseBodyTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/offway/core/common/response/ src/test/java/com/offway/core/common/response/ApiResponseBodyTest.java
git commit -m "feat: 공통 응답 래퍼 ApiResponseBody·PageResponse"
```

---

## Task 4: 전역 예외 핸들러 (`GlobalExceptionHandler`)

**Files:**
- Create: `src/main/java/com/offway/core/common/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/offway/core/common/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write the failing test**

standaloneSetup MockMvc라 Spring 컨텍스트·Docker 없이 돈다.

`src/test/java/com/offway/core/common/exception/GlobalExceptionHandlerTest.java`:

```java
package com.offway.core.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    enum DummyCode implements ErrorCode {
        CONFLICT_DUMMY("DUMMY-409", ErrorCategory.CONFLICT, "이미 존재합니다.");
        private final String code;
        private final ErrorCategory category;
        private final String message;
        DummyCode(String c, ErrorCategory cat, String m) { this.code = c; this.category = cat; this.message = m; }
        @Override public String code() { return code; }
        @Override public ErrorCategory category() { return category; }
        @Override public String message() { return message; }
    }

    static final class DummyException extends BaseException {
        private DummyException(ErrorCode ec) { super(ec); }
        static DummyException conflict() { return new DummyException(DummyCode.CONFLICT_DUMMY); }
    }

    @RestController
    static class DummyController {
        @GetMapping("/boom")
        String boom() { throw DummyException.conflict(); }

        @GetMapping("/unexpected")
        String unexpected() { throw new RuntimeException("예상 못한 오류"); }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DummyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void 도메인_예외는_status_code_detail을_래퍼로_내린다() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUMMY-409"))
                .andExpect(jsonPath("$.detail").value("이미 존재합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 예상못한_예외는_500과_COMMON_500_코드를_내린다() throws Exception {
        mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("COMMON-500"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.offway.core.common.exception.GlobalExceptionHandlerTest"`
Expected: FAIL — `GlobalExceptionHandler` 없음

- [ ] **Step 3: Write minimal implementation**

`GlobalExceptionHandler.java`:

```java
package com.offway.core.common.exception;

import com.offway.core.common.response.ApiResponseBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인 예외 — 클라이언트 계약 위반은 서버 입장에선 정상 동작이라 info
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponseBody<Void>> handleBase(BaseException e) {
        ErrorCode errorCode = e.errorCode();
        log.info("도메인 예외 code={} status={}", errorCode.code(), errorCode.category().httpStatus());
        return ResponseEntity.status(errorCode.category().httpStatus())
                .body(ApiResponseBody.fail(errorCode));
    }

    // Bean Validation 실패 → 400, "필드명: 메시지" 형식
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseBody<Void>> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String detail = fieldError == null
                ? "요청 형식이 올바르지 않습니다."
                : "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
        log.info("검증 실패 detail={}", detail);
        return ResponseEntity.status(ErrorCategory.BAD_REQUEST.httpStatus())
                .body(ApiResponseBody.fail(ErrorCategory.BAD_REQUEST, detail, "COMMON-400"));
    }

    // 예상 못한 서버 버그 — 스택 포함 error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseBody<Void>> handleUnexpected(Exception e) {
        log.error("예상치 못한 서버 오류", e);
        return ResponseEntity.status(ErrorCategory.INTERNAL.httpStatus())
                .body(ApiResponseBody.fail(ErrorCategory.INTERNAL, "일시적인 오류가 발생했습니다.", "COMMON-500"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.offway.core.common.exception.GlobalExceptionHandlerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/offway/core/common/exception/GlobalExceptionHandler.java src/test/java/com/offway/core/common/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: 전역 예외 핸들러 GlobalExceptionHandler"
```

---

## Task 5: 도메인 패키지 골격

**Files:**
- Create: `src/main/java/com/offway/core/{leave,trip,policy,transport,itinerary,external,user}/package-info.java`

(Java는 빈 디렉토리를 추적하지 않으므로, 각 도메인 패키지를 `package-info.java` 한 줄로 문서화하며 생성한다.)

- [ ] **Step 1: 7개 package-info 작성**

각 파일은 다음 형태 (도메인 설명만 교체):

`src/main/java/com/offway/core/leave/package-info.java`
```java
/** 연차·가용시간(LNT)·샌드위치 연휴·연차 컨설팅 도메인. */
package com.offway.core.leave;
```
`.../trip/package-info.java` → `/** 인구감소지역·관광지 추천 도메인 (TourAPI). */`
`.../policy/package-info.java` → `/** 7대 여행 지원 혜택 매칭 도메인 (수동 적재 데이터). */`
`.../transport/package-info.java` → `/** 교통·동선 도메인 (TAGO·TMAP·코레일/SR). */`
`.../itinerary/package-info.java` → `/** 일정표 자동 생성 도메인 (leave+trip+transport 조합). */`
`.../external/package-info.java` → `/** 외부 API 클라이언트 격리 (WebClient). API별 서브패키지로 분리. */`
`.../user/package-info.java` → `/** 사용자·인증 도메인 (Spring Security + OAuth2). */`

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/offway/core/
git commit -m "chore: 도메인 패키지 골격 (package-by-feature)"
```

---

## Task 6: 컨벤션 문서 (`CLAUDE.md` · testing-convention)

**Files:**
- Create: `CLAUDE.md`
- Create: `.claude/rules/testing-convention.md`

CLAUDE.md는 스펙 §4를 **프로젝트 지침 톤**으로 옮기고, 로컬 실행성 규칙을 더한다. 아래 섹션을 모두 포함한다 (내용 정본 = 스펙 §4.1~§4.14):

- [ ] **Step 1: `CLAUDE.md` 작성**

포함 섹션(제목 그대로):
1. `# OffWay 프로젝트 컨벤션` (서두: 언어=Java 25, Lombok, package-by-feature)
2. `## 로컬 실행성` — **핵심 불변식**:
   > designated 브랜치는 `local` 프로파일에서 **시크릿·외부 인프라 없이 부팅 가능**해야 한다. H2 인메모리로 뜨고, 외부 API 키·OAuth 시크릿·실 DB/Redis가 없어도 부팅 자체는 막히지 않는다(실제 호출만 실패). 이를 깨는 변경(부팅에 실 키·실 DB 강제)은 금지. 외부 클라이언트는 키가 없으면 비활성/stub으로 뜨게 설계한다.
3. `## 객체지향 설계 · 상수화 원칙` (§4.15 — 매직값 금지·enum·캡슐화·rich domain·추상화(port)·다형성·Java25 record/sealed)
4. `## Null 처리` (스펙 §4.1)
5. `## 도메인 예외 정책` (§4.2) + `### 커스텀 예외 형태` (§4.3) + `### 에러 코드` (§4.4)
5. `## 외래 키 · JPA 연관관계` (§4.5 — DB FK OFF / 애그리거트 내부만 연관관계 / 경계는 raw ID / N+1 가드)
6. `## DB 마이그레이션` (§4.6 — Flyway, `V{timestamp}__`, forward-only·additive·out-of-order, MySQL/H2 호환)
7. `## 트랜잭션 경계` (§4.7 — 외부 호출은 tx 밖, 영속화만 별도 빈, self-invocation)
8. `## 로깅` (§4.8 — `@Slf4j`, 마스킹, 레벨, placeholder)
9. `## DTO ↔ 도메인 매핑` (§4.9)
10. `## 컨트롤러 / OpenAPI` (§4.10 — `*Api` 인터페이스 분리, 응답 전수 문서화. example 객체화는 후속)
11. `## 응답 포맷` (§4.11 — `ApiResponseBody` 래퍼, 204 미사용, 3xx 예외)
12. `## 도메인 용어` (§4.12 — LNT·샌드위치 연휴·인구감소지역·7대 혜택)
13. `## 의존성 관리` (§4.13)
14. `## YAGNI / 가까운 미래` (§4.14)
15. `## 테스트` → `@.claude/rules/testing-convention.md` import 한 줄

- [ ] **Step 2: `.claude/rules/testing-convention.md` 작성 (Java/JUnit5)**

core의 testing-convention을 Java/JUnit5로 번역해 아래를 포함한다:
- 테스트 3분류: 단위(Spring·DB 없이 도메인·순수함수, 분기 망라) / 통합(컨트롤러→DB 실제 흐름, 외부는 stub 격리) / E2E(외부 실호출, `@Disabled` 또는 `@EnabledIfEnvironmentVariable` 격리, CI 제외)
- 가치 판단: "깨지면 비싼가·회귀 위험·변경 잦은가". 도메인 계산/정책·핵심 여정·HTTP 계약·외부 분기는 반드시.
- 분기 위치 결정 트리 (§core와 동일 8분기)
- 단위 테스트: 도메인과 같은 패키지, `@ParameterizedTest` 적극
- 통합 테스트: 엔드포인트당 3~5 시나리오, 응답 contract(`status·code·detail·data`) 단언 포함
- 모킹/Stub: 내부는 실제 빈, 외부 경계만 프로그래머블 stub(default 람다는 throw). `@MockBean` 남용·`@DirtiesContext`·`@ActiveProfiles` 컨텍스트 캐시 파괴 지양
- 네이밍: 단위 `{대상}Test`, 통합 `{시나리오}IntegrationTest`, E2E `{대상}E2ETest`. 메서드명은 한국어 서술
- 단언: 기본 JUnit5 `Assertions`, 컬렉션·객체그래프 깊은 비교는 AssertJ (둘 다 `spring-boot-starter-test` 포함)
- Docker 가드: Testcontainers 도입 시 `docker info` 선검증 (도입 전까지는 H2로 통합 테스트)

> 주의: core는 Kotlin·Testcontainers MySQL 전제다. OffWay는 초기엔 **H2로 통합 테스트**하고, Testcontainers 도입은 필요 시 별도 작업으로 승격한다(이 파일에 그때 반영).

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md .claude/rules/testing-convention.md
git commit -m "docs: 개발 컨벤션 CLAUDE.md + 테스트 컨벤션"
```

---

## Task 7 (선택): springdoc / Swagger UI — FE 탐색용

> FE가 Swagger로 API를 탐색하는 UX의 기반. **Spring Boot 4.1 호환 springdoc 버전 확인이 선행**이라 별도 Task로 둔다. 호환 버전이 아직 없으면 이 Task는 보류하고 Plan 2 이후 재시도한다.

**Files:**
- Modify: `build.gradle.kts`
- Test: 기존 `CoreApplicationTests` (부팅 확인)

- [ ] **Step 1: Boot 4 호환 springdoc 버전 조회**

https://central.sonatype.com 에서 `springdoc-openapi-starter-webmvc-ui`의 Spring Boot 4.x 호환 최신 안정 버전을 확인한다. (pre-release 제외. §4.13 의존성 규칙.)

- [ ] **Step 2: 의존성 추가 (호환 버전 확인된 경우만)**

`build.gradle.kts` `dependencies`에:
```kotlin
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:<확인한_버전>")
```

- [ ] **Step 3: 부팅 + Swagger 경로 확인**

Run: `./gradlew test --tests "com.offway.core.CoreApplicationTests"`
Expected: PASS. (실행 후 `http://localhost:8080/swagger-ui.html` 접근은 컨트롤러가 생기는 Plan 2 이후 의미가 커짐.)

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: springdoc-openapi (Swagger UI) 추가"
```

---

## 전체 검증

- [ ] `./gradlew test` 전체 GREEN
- [ ] `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` 이 시크릿 없이 부팅

## Self-Review 결과

- **스펙 커버리지**: §3 아키텍처(Task 5) · §4.1~4.14 컨벤션(Task 6) · §4.11 응답 래퍼(Task 3) · §4.2~4.4 예외(Task 2·4) · §5 공통 기반(Task 2·3·4) · 로컬 실행성(Task 1·6) 모두 태스크로 매핑됨. (§4.10 example 객체화, §6 커맨드, §7 템플릿은 **본 플랜 범위 밖** — Plan 2·3.)
- **타입 일관성**: `ErrorCode.code()/category()/message()`, `BaseException.errorCode()`, `ApiResponseBody.fail(ErrorCode)` / `.fail(ErrorCategory,String,String)`, `ErrorCategory.httpStatus()` 가 Task 2·3·4에서 동일 시그니처로 참조됨. 확인 완료.
- **미해결 리스크**: (a) Redis 자동설정이 로컬 부팅을 막을 가능성 → Task 1 Step 4에 exclude fallback 명시. (b) springdoc Boot4 호환 → Task 7을 선택·검증-선행으로 격리.

---

## 다음 (Plan 2 예고)

**외부 API 연결 전부.** 특일정보·TourAPI(국문관광정보·집중률)·TAGO·TMAP·코레일/SR + 인구감소지역/생활인구/로컬100 데이터. 각 API의 발급처(공공데이터포털 등)·엔드포인트·인증 방식 **리서치 → `external/<api>` 클라이언트 구현**. 리서치는 Plan 2 착수 시 진행한다.

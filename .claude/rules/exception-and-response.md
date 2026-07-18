# 예외 정책 · 응답 포맷

`CLAUDE.md` 가 import 한다. 예외 던지기 규칙과 응답 래퍼 규격을 담는다.

## 도메인 예외 정책 — 불변식 vs 계약

**판단 기준 한 줄: "멀쩡한 클라이언트가 정상 요청으로 여기 닿을 수 있나?"**

- **닿는다 → 계약 → 커스텀 예외** (400 / 409 / 403 / 502 등). `status`·`code` 가 코드에 박힌다.
- **못 닿는다 → 불변식 → `Objects.requireNonNull` / `if (!조건) throw new IllegalStateException(...)`** (500, 의도된 코드 버그 신호).

| 상황 | 누가 터뜨리나 | 범주 | 도구 | 결과 |
|---|---|---|---|---|
| 연차 일수가 음수 (요청 DTO) | 클라이언트 | 계약 | 커스텀 예외 | 400 |
| 이미 확정된 일정에 재확정 요청 | 클라이언트 | 계약 | 커스텀 예외 | 409 |
| TourAPI 응답 파싱 실패 | 외부 의존성 | 계약(도달 가능) | 커스텀 예외 | 502 |
| 서비스가 보장한 값이 도메인에서 어긋남 | 개발자(버그) | 불변식 | `require`/`check` | 500 |

- **도메인이 자기방어한다.** 도메인 메서드가 직접 커스텀 예외를 던지면 호출 위치(서비스·다른 도메인·테스트)와 무관하게 같은 결과가 나온다.
- **한 메서드에 불변식 검증과 계약 예외가 공존해도 정상.** 각 줄이 "누가 터뜨리나"라는 다른 질문에 답할 뿐이다.
- **검증은 입력 경계와 도메인 양쪽에 둔다.** 입력 경계(요청 DTO)는 *계약* 검증, 도메인 생성자는 *불변식* 검증(`require`). 도메인은 누가 만들든 스스로 유효함을 보장하는 최후의 보루다.

## 커스텀 예외 형태 (Java)

- 이름은 `{도메인명사}Exception` — `LeaveException`, `PolicyException`, `TourApiException`. 행위명이 아니라 도메인 명사.
- `BaseException`(RuntimeException 상속) + `HttpMappable` 을 따른다.
- **private 생성자 + static 팩토리 메서드.** 각 팩토리는 사유 하나 = `ErrorCode` 한 엔트리를 참조한다. status·메시지는 throw 지점에 흩어지지 않고 `ErrorCode` 한 곳에 모인다.

```java
public final class LeaveException extends BaseException {

    private LeaveException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static LeaveException invalidAnnualLeave() {
        return new LeaveException(LeaveErrorCode.INVALID_ANNUAL_LEAVE);
    }
}
```

호출부는 `throw LeaveException.invalidAnnualLeave();` 처럼 사유 이름만 읽으면 된다.

## 에러 코드 (`*ErrorCode` enum)

에러 응답은 사용자 문구가 아니라 **code**(예: `LEAVE-001`)로 사유를 식별한다.

- 도메인별 `*ErrorCode` enum 이 `code` · `category` · `message` 를 **single source** 로 보유하고 `ErrorCode` 인터페이스를 구현한다.
- **번호는 append-only** — 재사용·재배치 금지, 결번 유지(코드가 클라이언트 계약).
- status 는 `ErrorCategory` → `HttpStatus` 1:1 매핑이 소유한다. 예외는 status 를 직접 들지 않고 category 에서 파생한다.
- 성공 응답은 `code = "OK"`, 실패 응답만 도메인 code.

```java
public enum LeaveErrorCode implements ErrorCode {

    INVALID_ANNUAL_LEAVE("LEAVE-001", ErrorCategory.BAD_REQUEST, "연차 일수가 올바르지 않습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    LeaveErrorCode(String code, ErrorCategory category, String message) {
        this.code = code;
        this.category = category;
        this.message = message;
    }

    @Override public String code() { return code; }
    @Override public ErrorCategory category() { return category; }
    @Override public String message() { return message; }
}
```

## 메시지 톤 — detail 은 전부 사용자 대면

예외의 message 는 `GlobalExceptionHandler` 를 거쳐 응답 `detail` 로 클라이언트에 그대로 나간다.

- **누가 어떤 이유로 닿든 사용자가 본다고 가정**하고, 모든 message 는 고정된 사용자 친화 문구로 둔다.
- message 에 내부 식별자·사용자 입력 원본·구체 검증 사유·기술 용어·외부 API 원문을 담지 않는다.
- 디버깅에 필요한 구체 정보는 응답이 아니라 **로그·cause 체인**으로 남긴다.

## 응답 포맷 — 공통 래퍼

**모든 응답은 `ApiResponseBody<T>` 래퍼로 감싼다.** 컨트롤러는 `ApiResponseBody<T>` 를 반환하고 raw DTO / `ResponseEntity` 를 직접 노출하지 않는다.

```jsonc
{ "status": 200, "data": {...|null}, "detail": "...", "code": "OK|LEAVE-001|...", "pageResponse": {...|null} }
```

- 성공: `ApiResponseBody.ok(...)` / `ApiResponseBody.created(...)`. 실패: `GlobalExceptionHandler` 가 `ApiResponseBody.fail(...)` 로 매핑.
- **HTTP 204 미사용** — 래퍼가 항상 body 를 만들므로 "body 없음"이 본질인 204 와 충돌한다. 내릴 데이터가 없으면 200 + `ok()`(data=null).
- **3xx 리다이렉트는 예외** — body 가 아니라 `Location` 헤더를 소비하므로 `ResponseEntity<Void>` 를 직접 반환한다.
- 비기본 status(`201` 등)는 컨트롤러 메서드에 `@ResponseStatus` 명시. body 의 `status` 와 HTTP status 를 항상 일치시킨다.

## 전역 예외 핸들러

`GlobalExceptionHandler`(`@RestControllerAdvice`)가 매핑한다.

- `BaseException` → `errorCode` 의 status·code·detail 로 `fail`. 로그는 info(클라이언트 계약 위반은 서버 입장에서 정상).
- `MethodArgumentNotValidException`(Bean Validation) → 400, `"필드명: 메시지"` detail.
- 그 외 `Exception` → 500, 일반 code(`COMMON-500`). 스택 포함 error 로그. **일반 500 은 엔드포인트 계약이 아니므로 OpenAPI 문서화 대상에서 제외**(api-convention 참고).

package com.offway.core.common.response;

import com.offway.core.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 모든 API 응답을 감싸는 공통 래퍼.
 *
 * <p>HTTP 204 는 사용하지 않는다 — 래퍼가 항상 body 를 만들므로 내릴 데이터가 없으면 200 + {@link #ok()}(data=null) 로 응답한다. 3xx
 * 리다이렉트는 body 가 아니라 {@code Location} 헤더를 소비하므로 이 래퍼를 쓰지 않는다.
 *
 * @param status body 의 status. HTTP status 와 항상 일치시킨다.
 * @param data 성공 페이로드. 실패 시 null.
 * @param detail 사용자 대면 문구.
 * @param code 성공은 {@code OK}, 실패는 도메인 에러코드.
 * @param pageResponse 페이지네이션 메타. 없으면 null.
 * @param sources 출처를 표기해야 하는 기관들(#399) — <b>기관명까지 함께</b> 나간다. 앱은 "출처: ⓒ" 만
 *     붙이면 된다. <b>비어 있을 수 있다</b> — 우리가 만든 값만 실린 응답이다. {@link Attributed} 를
 *     구현한 data 에서만 채워진다
 */
@Builder(access = AccessLevel.PRIVATE)
public record ApiResponseBody<T>(
        int status,
        @Schema(nullable = true) T data,
        String detail,
        String code,
        @Schema(nullable = true) PageResponse pageResponse,
        @Schema(description = "출처를 표기해야 하는 기관. 기관명을 함께 주므로 앱은 \"출처: ⓒ\" 만 붙이면 된다. "
                        + "교통(TMAP·TAGO)은 표기 대상이 아니라 빠진다")
                List<DataSourceResponse> sources) {

    /** 빌려온 값이 없는 응답 — null 이 아니라 빈 목록이다. 앱이 유무를 분기하지 않게. */
    private static final List<DataSourceResponse> NO_SOURCES = List.of();

    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_DETAIL = "요청이 정상 처리되었습니다.";

    public static <T> ApiResponseBody<T> ok(T data) {
        return ok(data, null);
    }

    public static <T> ApiResponseBody<T> ok(T data, PageResponse pageResponse) {
        return success(HttpStatus.OK, data, pageResponse);
    }

    /** 내릴 데이터가 없는 성공 응답 (204 대신 200 + data=null). */
    public static <T> ApiResponseBody<T> ok() {
        return success(HttpStatus.OK, null, null);
    }

    /**
     * 내릴 데이터가 없고 <b>문구 자체가 결과</b>인 성공 응답 — 예: 탈퇴.
     *
     * <p>기본 detail("요청이 정상 처리되었습니다")로 충분하면 {@link #ok()} 를 쓴다. 이 오버로드는 data 가
     * null 이라 응답에서 사용자가 읽을 것이 detail 뿐인 경우를 위한 것이다.
     *
     * <p>이름을 {@code ok} 로 겹치지 않게 한 이유 — {@code ok(T)} 와 시그니처가 충돌해 {@code T} 가 String 일
     * 때 어느 쪽이 불릴지 읽는 사람이 알 수 없다.
     */
    public static <T> ApiResponseBody<T> okWithDetail(String detail) {
        return ApiResponseBody.<T>builder()
                .status(HttpStatus.OK.value())
                .detail(detail)
                .code(SUCCESS_CODE)
                .sources(NO_SOURCES)
                .build();
    }

    public static <T> ApiResponseBody<T> created(T data) {
        return success(HttpStatus.CREATED, data, null);
    }

    public static <T> ApiResponseBody<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.message());
    }

    /** detail 을 구체 사유로 덮어쓰는 실패 응답 (Bean Validation 필드 메시지 등). */
    public static <T> ApiResponseBody<T> fail(ErrorCode errorCode, String detail) {
        return ApiResponseBody.<T>builder()
                .status(errorCode.category().httpStatus().value())
                .detail(detail)
                .code(errorCode.code())
                .sources(NO_SOURCES)
                .build();
    }

    /**
     * status 를 프레임워크가 소유하는 실패 응답.
     *
     * <p>도메인 예외는 {@code ErrorCode} 의 category 가 status 를 소유하지만, Spring MVC 가 만드는 예외(406·413·503 등)는
     * 프레임워크가 이미 status 를 정해 들고 있다. 그 status 를 그대로 실어 **body 의 status 와 HTTP status 를 항상 일치**시킨다.
     * category 에서 파생하면 매핑이 없는 status 마다 둘이 어긋난다.
     */
    public static <T> ApiResponseBody<T> fail(HttpStatusCode status, ErrorCode errorCode) {
        return ApiResponseBody.<T>builder()
                .status(status.value())
                .detail(errorCode.message())
                .code(errorCode.code())
                .sources(NO_SOURCES)
                .build();
    }

    private static <T> ApiResponseBody<T> success(HttpStatus status, T data, PageResponse pageResponse) {
        return ApiResponseBody.<T>builder()
                .status(status.value())
                .data(data)
                .detail(SUCCESS_DETAIL)
                .code(SUCCESS_CODE)
                .pageResponse(pageResponse)
                .sources(sourcesOf(data))
                .build();
    }

    /**
     * 출처는 <b>data 가 스스로 밝힌다</b>(#399) — 컨트롤러가 손으로 나열하지 않는다.
     *
     * <p>손으로 넘기게 두면 화면이 늘 때마다 빠뜨릴 자리가 하나씩 는다. 표기 누락은 규정 위반이라
     * "가끔 빠진다" 가 허용되지 않는 값이다.
     *
     * <p>실패 응답에는 없다 — 내려간 데이터가 없으므로 빌려온 출처도 없다.
     */
    private static List<DataSourceResponse> sourcesOf(Object data) {
        return DataSourceResponse.of(collect(data));
    }

    /**
     * <b>목록도 본다.</b> 코스 목록처럼 {@code data} 가 {@code List} 인 응답이 있는데, 그 자리를 안 보면
     * 목록 화면에서만 출처가 조용히 사라진다 — 응답은 멀쩡해 보이고 표기만 빠진다.
     *
     * <p>중첩은 한 겹만 본다. 목록 안의 목록은 지금 없고, 있다면 그 자체가 응답 모양을 다시 볼 신호다.
     */
    private static Set<DataSource> collect(Object data) {
        if (data instanceof Attributed attributed) {
            return attributed.sources();
        }
        if (data instanceof Collection<?> items) {
            return items.stream()
                    .filter(Attributed.class::isInstance)
                    .map(Attributed.class::cast)
                    .flatMap(item -> item.sources().stream())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}

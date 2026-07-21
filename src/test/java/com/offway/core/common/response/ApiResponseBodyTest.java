package com.offway.core.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.offway.core.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseBodyTest {

    @Test
    void ok는_200과_OK코드를_싣는다() {
        ApiResponseBody<String> body = ApiResponseBody.ok("결과");

        assertEquals(200, body.status());
        assertEquals("결과", body.data());
        assertEquals("OK", body.code());
        assertNull(body.pageResponse());
    }

    @Test
    void 내릴_데이터가_없으면_200에_data가_null이다() {
        ApiResponseBody<Void> body = ApiResponseBody.ok();

        assertEquals(200, body.status());
        assertNull(body.data());
        assertEquals("OK", body.code());
    }

    @Test
    void created는_201을_싣는다() {
        ApiResponseBody<String> body = ApiResponseBody.created("생성됨");

        assertEquals(201, body.status());
        assertEquals("생성됨", body.data());
        assertEquals("OK", body.code());
    }

    @Test
    void fail은_errorCode의_status_code_detail을_싣는다() {
        ApiResponseBody<Void> body = ApiResponseBody.fail(CommonErrorCode.INTERNAL_ERROR);

        assertEquals(500, body.status());
        assertEquals("COMMON-500", body.code());
        assertEquals(CommonErrorCode.INTERNAL_ERROR.message(), body.detail());
        assertNull(body.data());
    }

    @Test
    void fail은_detail을_덮어쓸_수_있다() {
        ApiResponseBody<Void> body = ApiResponseBody.fail(CommonErrorCode.INVALID_REQUEST, "annualLeaveDays: 0 이상이어야 합니다.");

        assertEquals(400, body.status());
        assertEquals("COMMON-400", body.code());
        assertEquals("annualLeaveDays: 0 이상이어야 합니다.", body.detail());
    }

    @Test
    void 페이지_응답을_함께_싣는다() {
        PageResponse page = new PageResponse(0, 20, 89L, 5);

        ApiResponseBody<String> body = ApiResponseBody.ok("결과", page);

        assertEquals(200, body.status());
        assertEquals(page, body.pageResponse());
        assertEquals(89L, body.pageResponse().totalElements());
    }
}

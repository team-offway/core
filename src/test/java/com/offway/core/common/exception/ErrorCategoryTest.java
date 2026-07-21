package com.offway.core.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCategoryTest {

    @ParameterizedTest
    @CsvSource({
        "BAD_REQUEST, 400",
        "UNAUTHORIZED, 401",
        "FORBIDDEN, 403",
        "NOT_FOUND, 404",
        "CONFLICT, 409",
        "EXTERNAL_API, 502",
        "INTERNAL, 500"
    })
    void 카테고리는_지정된_HTTP_상태로_매핑된다(ErrorCategory category, int expectedStatus) {
        assertEquals(expectedStatus, category.httpStatus().value());
    }

    @ParameterizedTest
    @EnumSource(ErrorCategory.class)
    void 모든_카테고리는_HTTP_상태를_빠짐없이_가진다(ErrorCategory category) {
        assertNotNull(category.httpStatus(), "httpStatus 매핑이 없는 카테고리: " + category);
    }
}

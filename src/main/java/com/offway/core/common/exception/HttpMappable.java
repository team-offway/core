package com.offway.core.common.exception;

import org.springframework.http.HttpStatus;

/** HTTP 응답 상태로 매핑될 수 있는 예외. */
public interface HttpMappable {

    HttpStatus httpStatus();
}

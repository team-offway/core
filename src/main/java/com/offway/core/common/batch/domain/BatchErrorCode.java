package com.offway.core.common.batch.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/** 배치 수동 실행 에러 코드(#537). 번호는 append-only — 재사용·재배치하지 않는다. */
public enum BatchErrorCode implements ErrorCode {

    UNKNOWN_BATCH("BATCH-001", ErrorCategory.BAD_REQUEST, "알 수 없는 배치입니다."),

    /**
     * 같은 배치가 이미 도는 중.
     *
     * <p>겹쳐 돌면 외부 한도를 두 배로 태우고, 교체 중인 데이터를 서로 밟는다. 잠시 뒤 다시 누르면 된다.
     */
    ALREADY_RUNNING("BATCH-002", ErrorCategory.CONFLICT, "이미 실행 중인 배치입니다. 잠시 뒤 다시 시도해 주세요."),

    /** 배치 안에서 예외가 났다. 구체 사유는 응답이 아니라 로그에 남긴다. */
    RUN_FAILED("BATCH-003", ErrorCategory.INTERNAL, "배치 실행에 실패했습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    BatchErrorCode(String code, ErrorCategory category, String message) {
        this.code = code;
        this.category = category;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String message() {
        return message;
    }
}

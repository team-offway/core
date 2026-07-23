package com.offway.core.policy.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 정책 관련 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum PolicyErrorCode implements ErrorCode {

    /** 요청한 정책이 없거나 노출 대상이 아님(미검증 포함). */
    POLICY_NOT_FOUND("POLICY-001", ErrorCategory.NOT_FOUND, "요청한 정책을 찾을 수 없습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    PolicyErrorCode(String code, ErrorCategory category, String message) {
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

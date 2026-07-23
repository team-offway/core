package com.offway.core.policy.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 정책 관련 예외. */
public final class PolicyException extends BaseException {

    private PolicyException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 정책이 없거나 노출 대상이 아님. */
    public static PolicyException notFound() {
        return new PolicyException(PolicyErrorCode.POLICY_NOT_FOUND);
    }
}

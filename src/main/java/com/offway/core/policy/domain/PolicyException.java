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

    /** 신청 주소가 https 가 아니거나 호스트가 없다(#344). */
    public static PolicyException insecureApplyUrl() {
        return new PolicyException(PolicyErrorCode.INSECURE_APPLY_URL);
    }

    /** 시작일이 종료일보다 늦다(#344). */
    public static PolicyException invalidPeriod() {
        return new PolicyException(PolicyErrorCode.INVALID_PERIOD);
    }

    /** 같은 분류가 이미 같은 기간에 노출된다(#344) — 뱃지가 두 개 뜬다. */
    public static PolicyException duplicateActiveType() {
        return new PolicyException(PolicyErrorCode.DUPLICATE_ACTIVE_TYPE);
    }
}

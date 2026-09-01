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
    POLICY_NOT_FOUND("POLICY-001", ErrorCategory.NOT_FOUND, "요청한 정책을 찾을 수 없습니다."),

    /** 신청 주소가 https 가 아니거나 호스트가 없음(#344). 앱이 웹뷰로 여는 값이다. */
    INSECURE_APPLY_URL("POLICY-002", ErrorCategory.BAD_REQUEST, "신청 주소는 https 로 시작하는 올바른 주소여야 합니다."),

    /** 시작일이 종료일보다 늦음(#344). 그대로 두면 어떤 날짜에도 뱃지가 안 뜬다. */
    INVALID_PERIOD("POLICY-003", ErrorCategory.BAD_REQUEST, "노출 시작일은 종료일보다 늦을 수 없습니다."),

    /**
     * 같은 분류의 정책이 이미 같은 기간에 노출된다(#344).
     *
     * <p>뱃지 문구를 분류가 소유하므로, 둘 다 노출되면 <b>글자까지 같은 뱃지가 두 개</b> 뜬다.
     */
    DUPLICATE_ACTIVE_TYPE("POLICY-004", ErrorCategory.CONFLICT, "같은 분류의 정책이 이미 그 기간에 노출됩니다.");

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

package com.offway.core.curation.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 큐레이션 링크 관련 예외(#341). */
public final class CurationException extends BaseException {

    private CurationException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 링크 주소가 https 가 아니다. */
    public static CurationException insecureLinkUrl() {
        return new CurationException(CurationErrorCode.INSECURE_LINK_URL);
    }

    /** 상시 노출이 아닌데 종료일이 없다. */
    public static CurationException endDateRequired() {
        return new CurationException(CurationErrorCode.END_DATE_REQUIRED);
    }

    /** 종료일이 시작일보다 앞이다. */
    public static CurationException periodReversed() {
        return new CurationException(CurationErrorCode.PERIOD_REVERSED);
    }

    /** 칩 문구가 너무 길다. */
    public static CurationException chipTextTooLong() {
        return new CurationException(CurationErrorCode.CHIP_TEXT_TOO_LONG);
    }

    /** 노출할 화면을 하나도 고르지 않았다. */
    public static CurationException surfaceRequired() {
        return new CurationException(CurationErrorCode.SURFACE_REQUIRED);
    }
}

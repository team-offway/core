package com.offway.core.transport.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 교통(transport) 관련 예외. */
public final class TransportException extends BaseException {

    private TransportException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 앱이 보낸 출발지 코드를 풀 수 없음 — 허브가 사라졌거나 값이 망가졌다. */
    public static TransportException unknownOriginCode() {
        return new TransportException(TransportErrorCode.UNKNOWN_ORIGIN_CODE);
    }

    /** 출발지 좌표가 반쪽만 옴 — 위도·경도는 함께여야 한다. */
    public static TransportException partialOriginCoordinate() {
        return new TransportException(TransportErrorCode.PARTIAL_ORIGIN_COORDINATE);
    }
}

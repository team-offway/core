package com.offway.core.common.batch.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 배치 수동 실행 예외(#537). */
public final class BatchException extends BaseException {

    private BatchException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static BatchException unknownBatch() {
        return new BatchException(BatchErrorCode.UNKNOWN_BATCH);
    }

    public static BatchException alreadyRunning() {
        return new BatchException(BatchErrorCode.ALREADY_RUNNING);
    }

    public static BatchException runFailed() {
        return new BatchException(BatchErrorCode.RUN_FAILED);
    }
}

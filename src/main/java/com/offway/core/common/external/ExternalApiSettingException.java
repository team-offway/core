package com.offway.core.common.external;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

public final class ExternalApiSettingException extends BaseException {

    private ExternalApiSettingException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static ExternalApiSettingException unknownApi() {
        return new ExternalApiSettingException(ExternalApiSettingErrorCode.UNKNOWN_API);
    }

    public static ExternalApiSettingException invalidBatchLimit() {
        return new ExternalApiSettingException(ExternalApiSettingErrorCode.INVALID_BATCH_LIMIT);
    }

    public static ExternalApiSettingException batchLimitOverDailyLimit() {
        return new ExternalApiSettingException(
                ExternalApiSettingErrorCode.BATCH_LIMIT_OVER_DAILY_LIMIT);
    }
}

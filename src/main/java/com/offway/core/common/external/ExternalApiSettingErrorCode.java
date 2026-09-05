package com.offway.core.common.external;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 연동 설정 오류(#403).
 *
 * <p>번호는 append-only 다 — 재사용·재배치하지 않고 결번을 유지한다. 코드가 클라이언트 계약이다.
 */
public enum ExternalApiSettingErrorCode implements ErrorCode {

    /** 어드민이 고른 연동 이름이 코드의 enum 에 없다. 화면이 목록을 서버에서 받으므로 정상 경로로는 안 닿는다. */
    UNKNOWN_API("EXTAPI-001", ErrorCategory.BAD_REQUEST, "알 수 없는 외부 API 입니다."),

    INVALID_BATCH_LIMIT("EXTAPI-002", ErrorCategory.BAD_REQUEST, "배치 상한은 0 이상이어야 합니다."),

    /** 일일 한도보다 큰 상한은 무제한과 같은데 화면에는 제한이 걸린 것처럼 보인다. */
    BATCH_LIMIT_OVER_DAILY_LIMIT(
            "EXTAPI-003", ErrorCategory.BAD_REQUEST, "배치 상한은 일일 한도를 넘을 수 없습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    ExternalApiSettingErrorCode(String code, ErrorCategory category, String message) {
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

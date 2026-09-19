package com.offway.core.transport.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * transport 도메인 에러 코드(#590).
 *
 * <p>번호는 append-only 다 — 재사용·재배치를 하지 않고 결번을 유지한다(코드가 클라이언트 계약이다).
 */
public enum TransportErrorCode implements ErrorCode {

    /**
     * 출발지 코드를 풀 수 없다.
     *
     * <p><b>왜 조용히 폴백하지 않나.</b> 앱이 들고 있던 코드의 허브가 시드에서 사라졌거나(폐역) 값이
     * 망가진 경우다. 여기서 기본 출발지로 바꿔 코스를 만들면 사용자는 <b>엉뚱한 곳에서 출발하는
     * 코스</b>를 받고, 그것이 틀렸다는 사실을 아무도 모른다. 다시 고르게 하는 편이 낫다.
     *
     * <p>메시지에 코드값을 담지 않는다 — detail 은 그대로 사용자에게 나가는 자리다.
     */
    UNKNOWN_ORIGIN_CODE("TRANSPORT-001", ErrorCategory.BAD_REQUEST, "출발지를 찾을 수 없습니다. 다시 선택해 주세요.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    TransportErrorCode(String code, ErrorCategory category, String message) {
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

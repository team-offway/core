package com.offway.core.common.exception;

/**
 * 에러 사유의 single source — 도메인별 {@code *ErrorCode} enum 이 구현한다.
 *
 * <p>{@code code} 는 클라이언트 계약이므로 append-only 로 관리한다(재사용·재배치 금지, 결번 유지).
 */
public interface ErrorCode {

    /** 사유 식별자. 예: {@code LEAVE-001}. */
    String code();

    /** HTTP status 를 파생시키는 범주. */
    ErrorCategory category();

    /** 응답 detail 로 그대로 노출되는 사용자 대면 문구. */
    String message();
}

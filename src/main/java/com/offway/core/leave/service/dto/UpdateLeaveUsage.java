package com.offway.core.leave.service.dto;

import java.time.LocalDate;

/**
 * 연차 사용 내역 수정 커맨드 — 서비스 내부용(#267).
 *
 * <p>세 필드가 모두 <b>선택</b>이라 wrapper 타입이다. null 은 "안 보냈다" 이고 그 필드는 그대로 둔다.
 *
 * @param usedOn 새 사용일. null 이면 그대로
 * @param days 새 일수(0.25 단위 양수). null 이면 그대로
 * @param reason 새 사유. null 이면 그대로, 빈 문자열이면 지운다
 */
public record UpdateLeaveUsage(LocalDate usedOn, Double days, String reason) {}

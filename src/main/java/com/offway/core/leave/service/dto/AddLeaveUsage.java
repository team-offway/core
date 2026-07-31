package com.offway.core.leave.service.dto;

import java.time.LocalDate;

/**
 * 연차 사용 내역 추가 커맨드 — 서비스 내부용.
 *
 * @param usedOn 연차를 쓴(또는 되돌린) 날
 * @param days 증감(0.5 단위). 사용은 양수, 취소는 음수
 * @param reason 사유 (선택)
 * @param courseId 이 내역을 만든 코스 (수동 입력이면 null)
 */
public record AddLeaveUsage(LocalDate usedOn, double days, String reason, Long courseId) {
}

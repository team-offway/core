package com.offway.core.leave.service.dto;

import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;

/**
 * 가용시간 산출 커맨드 — 서비스 내부용. 컨트롤러 계약(요청 DTO)과 분리한다.
 *
 * <p>날짜 구간의 유효성(순서·상한)은 요청 DTO 경계에서 계약 검증을 마친 뒤 이 커맨드로 넘어온다.
 *
 * @param startDate 여행 시작일
 * @param endDate 여행 종료일
 * @param transport 이동수단
 * @param halfDayStart 출발일을 반차로 쓰는가
 */
public record CreateAvailableTime(LocalDate startDate, LocalDate endDate, TransportMode transport, boolean halfDayStart) {
}

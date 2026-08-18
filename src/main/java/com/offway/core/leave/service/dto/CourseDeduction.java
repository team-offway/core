package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.StartDayLeave;

/**
 * 코스 하나로 깎인 연차 — 다른 도메인(itinerary)이 차감량을 다시 계산할 때 보는 값(#170).
 *
 * <p>{@code LeaveUsage} 엔티티를 그대로 넘기지 않는다. 필요한 것은 두 값뿐인데 엔티티를 건네면 코스 쪽이
 * 연차 내역을 직접 고칠 수 있게 되고, 그 순간 "연차는 leave 가 소유한다" 가 무너진다.
 *
 * @param days 지금 깎여 있는 일수
 * @param startDayLeave 차감할 때 첫날에 쓴 연차 — 날짜가 바뀌어도 유지되는 사용자의 선택이다
 */
public record CourseDeduction(double days, StartDayLeave startDayLeave) {}

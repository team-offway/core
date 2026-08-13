package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.AvailableTime;
import com.offway.core.leave.domain.TripPeriod;

/**
 * 가용시간 산출 결과 — 확정된 구간과 그 구간의 가용 정보.
 *
 * <p>구간을 함께 내리는 이유는 기간스타일 모드에서 <b>클라이언트가 날짜를 모르기 때문</b>이다. 서버가 "가장 가까운 실제
 * 구간" 을 골랐으니 그게 며칠인지 알려줘야 화면에 띄울 수 있다. 날짜를 직접 고른 모드에서는 받은 값의 확인(echo)이 된다.
 *
 * @param period 확정된 여행 날짜 구간
 * @param availableTime 그 구간의 여행일수·소모 연차·이동 한계
 */
public record AvailableTimeResult(TripPeriod period, AvailableTime availableTime) {
}

package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.AvailableTime;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.leave.domain.TripPeriod;

/**
 * 가용시간 산출 결과 — 확정된 구간과 그 구간의 가용 정보.
 *
 * <p>구간을 함께 내리는 이유는 기간스타일 모드에서 <b>클라이언트가 날짜를 모르기 때문</b>이다. 서버가 "가장 가까운 실제
 * 구간" 을 골랐으니 그게 며칠인지 알려줘야 화면에 띄울 수 있다. 날짜를 직접 고른 모드에서는 받은 값의 확인(echo)이 된다.
 *
 * <p><b>첫날 단위도 함께 내린다.</b> 출발 시각이 거기서 도출되는데, 응답 dto 가 그 규칙을 다시 알 필요는 없다 —
 * 값을 들고 있는 쪽에게 물으면 된다.
 *
 * @param period 확정된 여행 날짜 구간
 * @param availableTime 그 구간의 여행일수·소모 연차·이동 한계
 * @param startDayLeave 첫날에 쓴 연차. 요청 값이 그대로 흐른다
 */
public record AvailableTimeResult(TripPeriod period, AvailableTime availableTime, StartDayLeave startDayLeave) {}

package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.BusStopAccess;

/**
 * TAGO 버스정류소정보(base {@code BusSttnInfoInqireService}) 조회 port. 좌표 주변의 정류소를 가까운 순으로 준다.
 *
 * <p>대중교통 <b>접근성</b> 판정에 쓴다 — "이 관광지에 버스가 서기는 하나". 정류소 위치는 잘 바뀌지 않아 여행 계획 시점(미래
 * 날짜)에도 유효하다. 반면 이 API 는 시간표를 주지 않으므로 "몇 시에 오는가" 는 답할 수 없다.
 *
 * <p>결과는 세 상태를 구분한다({@link BusStopAccess}): 주변 정류소 있음 / 주변에 없음 / 조회 불가. 인구감소지역은 실제로
 * 정류소가 없는 곳이 흔해서 "없음" 은 오류가 아니라 안내할 가치가 있는 정상 결과다.
 *
 * <p><b>⚠️ 커버리지 한계(실호출로 확인).</b> TAGO 시내버스는 전국이 아니라 <b>138개 지자체</b>만 담는다. 서울조차 빠져
 * 있고(별도 TOPIS), 우리 89개 인구감소지역 중 <b>정선·평창·영월·삼척·양구·화천·예산·강진·담양·보성·영광·화순</b> 등이
 * 빠져 있다. 미커버 지역은 오류가 아니라 {@code resultCode=00} + 빈 결과로 와서 현재 {@code NoStopNearby} 로 해석된다
 * — 즉 <b>"버스가 없다" 와 "데이터가 없다" 가 구분되지 않는다.</b> 이 결과로 "대중교통이 어렵다" 고 안내하면 틀린 말이
 * 될 수 있다. 커버 여부 판별({@code getCtyCodeList} 기반)은 별도 이슈로 분리했다.
 */
public interface BusStopClient {

    /**
     * 좌표 주변의 버스 정류소를 가까운 순으로 조회한다.
     *
     * @param lat 위도
     * @param lng 경도
     * @return {@link BusStopAccess} — 있음/주변에 없음/조회 불가
     */
    BusStopAccess nearbyStops(double lat, double lng);
}

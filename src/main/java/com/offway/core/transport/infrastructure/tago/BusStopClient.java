package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.BusCoverage;
import com.offway.core.transport.domain.BusStopAccess;
import java.util.Optional;

/**
 * TAGO 버스정류소정보(base {@code BusSttnInfoInqireService}) 조회 port. 좌표 주변의 정류소를 가까운 순으로 준다.
 *
 * <p>대중교통 <b>접근성</b> 판정에 쓴다 — "이 관광지에 버스가 서기는 하나". 정류소 위치는 잘 바뀌지 않아 여행 계획 시점(미래
 * 날짜)에도 유효하다. 반면 이 API 는 시간표를 주지 않으므로 "몇 시에 오는가" 는 답할 수 없다.
 *
 * <p>결과는 네 상태를 구분한다({@link BusStopAccess}): 주변 정류소 있음 / 주변에 없음 / 미커버 / 조회 불가. 인구감소지역은
 * 실제로 정류소가 없는 곳이 흔해서 "없음" 은 오류가 아니라 안내할 가치가 있는 정상 결과다.
 *
 * <p><b>⚠️ 커버리지 한계(실호출로 확인).</b> TAGO 시내버스는 전국이 아니라 <b>138개 지자체</b>만 담는다. 서울조차 빠져
 * 있고(별도 TOPIS), 우리 89개 인구감소지역 중 <b>13곳</b>(정선·평창·영월·삼척·양구·화천·강원 고성·예산·강진·담양·보성·
 * 영광·화순)이 빠져 있다. 미커버 지역은 오류가 아니라 {@code resultCode=00} + 빈 결과로 오기 때문에, 그냥 두면
 * "정류소 없음" 과 구분되지 않아 <b>"정선에 버스가 없다" 는 틀린 안내</b>가 나간다.
 *
 * <p>그래서 {@link #coveredCities()} 로 커버 여부를 먼저 판별한다. 이 판별은 {@code BusAccessService} 가 소유하고,
 * 미커버 지역은 정류소 조회 없이 {@code NotCovered} 로 답한다.
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

    /**
     * TAGO 시내버스가 담는 도시 목록({@code getCtyCodeList}). 조회 실패면 빈 Optional.
     *
     * @return 138개 지자체 목록 — 커버 여부 판별의 근거
     */
    Optional<BusCoverage> coveredCities();
}

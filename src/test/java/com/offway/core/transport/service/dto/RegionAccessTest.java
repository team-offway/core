package com.offway.core.transport.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.RegionArrival;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 도착 지점을 다른 수단으로 바꿔 잡는 규칙(#97).
 *
 * <p>여기서 틀리면 코스가 지역 반대편부터 짜인다 — 화면에는 정상으로 보이고 동선만 조용히 나빠지는 종류의 회귀다.
 */
class RegionAccessTest {

    private static final Coordinate 완도군청 = new Coordinate(34.3111, 126.7550);
    private static final Coordinate 먼_역 = new Coordinate(34.7900, 126.3900);
    private static final Coordinate 읍내_터미널 = new Coordinate(34.3120, 126.7560);

    private static RegionArrival 시외버스(Coordinate point) {
        return new RegionArrival(TransitMode.INTERCITY_BUS, "완도", point);
    }

    @Test
    void 운행_편을_찾았으면_더_가까운_터미널이_있어도_열차를_지킨다() {
        // 도착 시각을 아는 결과는 이것뿐이다. 지점 몇 ㎞ 를 얻자고 시각을 버리면 첫날이 통째로 "하루 전부" 가 된다.
        TrainLeg leg = TrainLeg.of("KTX", LocalDateTime.of(2026, 9, 1, 8, 0), LocalDateTime.of(2026, 9, 1, 11, 0));
        RegionAccess 열차 = RegionAccess.available("서울", "완도역", 먼_역, leg);

        assertSame(열차, 열차.orNearer(완도군청, 시외버스(읍내_터미널)));
    }

    @Test
    void 역이_아예_없으면_터미널을_도착_지점으로_잡는다() {
        // 예전에는 여기서 도착 지점이 없어 출발지 좌표로 되돌아갔다 — 서울에서 출발하면
        // "완도 장소들 중 서울에서 가까운 곳" 부터 이어붙는 동선이 나온다.
        RegionAccess 결과 = RegionAccess.noStation(null, null).orNearer(완도군청, 시외버스(읍내_터미널));

        assertEquals(RegionAccess.Status.POINT_ONLY, 결과.status());
        assertEquals(TransitMode.INTERCITY_BUS, 결과.mode());
        assertEquals("완도", 결과.toName());
        assertEquals(읍내_터미널, 결과.arrivalPoint().orElseThrow());
    }

    @Test
    void 역은_있지만_터미널이_더_가까우면_터미널로_바꾼다() {
        // 최근접 역이 30~50㎞ 밖인 지역이 아홉 곳이다(양양·합천·태안·진도·완도·함양·양구·산청·고령).
        RegionAccess 결과 =
                RegionAccess.noServiceOnDate("서울", "완도역", 먼_역).orNearer(완도군청, 시외버스(읍내_터미널));

        assertEquals(TransitMode.INTERCITY_BUS, 결과.mode());
        assertEquals(읍내_터미널, 결과.arrivalPoint().orElseThrow());
    }

    @Test
    void 역이_더_가까우면_사유를_보존한_채_그대로_둔다() {
        // POINT_ONLY 로 갈아치우면 "조회 실패" 라는 사유가 사라져 외부 장애가 화면에서 조용해진다.
        RegionAccess 조회실패 = RegionAccess.unavailable("서울", "완도역", new Coordinate(34.3115, 126.7555));

        RegionAccess 결과 = 조회실패.orNearer(완도군청, 시외버스(new Coordinate(34.4000, 126.8000)));

        assertSame(조회실패, 결과);
        assertEquals(RegionAccess.Status.UNAVAILABLE, 결과.status());
    }

    @Test
    void 후보가_하나도_없으면_원래_결과를_그대로_둔다() {
        RegionAccess 역없음 = RegionAccess.noStation(null, null);

        assertSame(역없음, 역없음.orNearer(완도군청, null, null));
    }

    @Test
    void 섬이라_항구만_있으면_배로_내린다() {
        Coordinate 울릉군청 = new Coordinate(37.4845, 130.9057);
        RegionArrival 도동항 = new RegionArrival(TransitMode.FERRY, "울릉_도동", new Coordinate(37.4838, 130.9053));

        RegionAccess 결과 = RegionAccess.noStation(null, null).orNearer(울릉군청, null, 도동항);

        assertEquals(TransitMode.FERRY, 결과.mode());
        assertEquals("울릉_도동", 결과.toName());
    }

    @Test
    void 지점만_아는_결과는_도착_시각을_말하지_않는다() {
        // 버스·여객선 구간 조회창이 오늘~+2일뿐이라 미래 날짜 시각을 지어낼 수 없다. 모르는 것은 모른다고 답한다.
        RegionAccess 지점만 = RegionAccess.pointOnly(시외버스(읍내_터미널));

        assertTrue(지점만.arrivalAt().isEmpty());
        assertTrue(지점만.arrivalPoint().isPresent());
    }
}

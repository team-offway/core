package com.offway.core.transport.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.RegionArrival;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.domain.TransitMode;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private static final LocalDate 여행일 = LocalDate.of(2026, 9, 1);

    private static RegionArrival 시외버스(Coordinate point) {
        return new RegionArrival(TransitMode.INTERCITY_BUS, "NAI5911401", "완도", point);
    }

    @Test
    void 운행_편을_찾았으면_더_가까운_터미널이_있어도_열차를_지킨다() {
        // 도착 시각을 아는 결과는 이것뿐이다. 지점 몇 ㎞ 를 얻자고 시각을 버리면 첫날이 통째로 "하루 전부" 가 된다.
        TrainLeg leg = TrainLeg.of("KTX", LocalDateTime.of(2026, 9, 1, 8, 0), LocalDateTime.of(2026, 9, 1, 11, 0));
        RegionAccess 열차 = RegionAccess.available("서울", "완도역", 먼_역, leg, List.of());

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
        RegionArrival 도동항 = new RegionArrival(TransitMode.FERRY, "SEA43113", "울릉_도동", new Coordinate(37.4838, 130.9053));

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

    @Test
    void 저장해_둔_소요시간이_있으면_출발_시각에_얹어_도착을_만든다() {
        // 시간표는 못 물어도 소요시간은 안다 — 대안은 "하루 전부" 라 오전 일정이 들어가 버린다(#107).
        RegionAccess 버스 = RegionAccess.pointOnly(시외버스(읍내_터미널)).withDuration(150);

        LocalDateTime 도착 = 버스.arrivalAt(여행일, LocalTime.of(8, 0)).orElseThrow();

        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 30), 도착);
    }

    @Test
    void 소요시간을_모르면_도착_시각도_모른다() {
        // 여기서 지어내면 조회 실패가 조용히 첫날을 깎는다.
        RegionAccess 버스 = RegionAccess.pointOnly(시외버스(읍내_터미널));

        assertTrue(버스.arrivalAt(여행일, LocalTime.of(8, 0)).isEmpty());
    }

    @Test
    void 실제_운행_편이_있으면_소요시간보다_그것을_쓴다() {
        // 편을 찾았다는 것은 시각까지 안다는 뜻이라, 추정으로 덮을 이유가 없다.
        TrainLeg leg = TrainLeg.of("KTX", LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 1, 11, 0));
        RegionAccess 열차 = RegionAccess.available("서울", "완도역", 먼_역, leg, List.of()).withDuration(999);

        assertEquals(LocalDateTime.of(2026, 9, 1, 11, 0), 열차.arrivalAt(여행일, LocalTime.of(8, 0)).orElseThrow());
    }

    @Test
    void 같은_소요시간을_다시_얹으면_새_객체를_만들지_않는다() {
        RegionAccess 버스 = RegionAccess.pointOnly(시외버스(읍내_터미널)).withDuration(150);

        assertSame(버스, 버스.withDuration(150));
    }

    @Test
    void 자차는_지역_자체가_도착_지점이다() {
        // 역·터미널·항구가 아니라 지역으로 바로 간다. 여기가 비면 화면이 카드를 통째로 접는다(#379).
        RegionAccess 자차 = RegionAccess.car("서울", "완도", 완도군청, 210, 350);

        assertEquals(TransitMode.CAR, 자차.mode());
        assertEquals("완도", 자차.toName());
        assertEquals(완도군청, 자차.arrivalPoint().orElseThrow());
        assertEquals(210, 자차.durationMinutes());
        assertEquals(350, 자차.distanceKm());
    }

    @Test
    void 자차도_출발_지점명을_싣는다() {
        // 서버가 좌표에서 만들지는 못하고, 저장할 때 앱이 실어 보낸 값을 그대로 되돌려준다(#382).
        assertEquals("서울", RegionAccess.car("서울", "완도", 완도군청, 210, 350).fromName());
    }

    @Test
    void 출발_지점명을_모르면_비워_둔다() {
        // 지오코딩이 실패했거나 이 필드를 모르는 앱이다. 지어내면 화면이 틀린 지명을 보여준다.
        assertNull(RegionAccess.car(null, "완도", 완도군청, 210, 350).fromName());
    }

    @Test
    void 자차는_나서는_시각에_이동시간을_얹어_도착을_안다() {
        // 운행 편이 없어도 도착 시각을 아는 유일한 수단이다 — 그래서 상태가 AVAILABLE 이다.
        RegionAccess 자차 = RegionAccess.car("서울", "완도", 완도군청, 210, 350);

        assertEquals(RegionAccess.Status.AVAILABLE, 자차.status());
        assertEquals(
                LocalDateTime.of(2026, 9, 1, 11, 30),
                자차.arrivalAt(여행일, LocalTime.of(8, 0)).orElseThrow());
    }

    @Test
    void 자차에는_대안이_없다() {
        // 자차로 가기로 한 사용자에게 "시외버스로도 갈 수 있다" 를 늘어놓는 것은 정보가 아니다.
        assertTrue(RegionAccess.car("서울", "완도", 완도군청, 210, 350).alternatives().isEmpty());
    }

    @Test
    void 같은_거리를_다시_얹으면_새_객체를_만들지_않는다() {
        RegionAccess 버스 = RegionAccess.pointOnly(시외버스(읍내_터미널)).withDistanceKm(300);

        assertSame(버스, 버스.withDistanceKm(300));
    }

    // ── 시간표가 붙으면 상태도 올라간다(#422) ─────────────────────────────

    private static Departure 편(int 출발시, int 도착시) {
        LocalDate 여행일 = LocalDate.of(2026, 9, 4);
        return new Departure("우등", 여행일.atTime(출발시, 40), 여행일.atTime(도착시, 40));
    }

    /**
     * <b>{@code POINT_ONLY} 인데 시간표가 실려 나갔다.</b>
     *
     * <p>그 상태는 "아직 안 물었다" 는 뜻인데 물어서 편이 나왔으니 서로를 부정한다. 화면은 목록만
     * 보고 그려 멀쩡했지만, 상태를 믿는 쪽(첫날 재정렬·로그)이 나중에 어긋난다.
     */
    @Test
    void 시간표가_붙으면_지점만_알던_상태가_올라간다() {
        RegionAccess 지점만 = RegionAccess.pointOnly(시외버스(읍내_터미널));
        assertEquals(RegionAccess.Status.POINT_ONLY, 지점만.status());

        RegionAccess 시간표붙음 = 지점만.withDepartures(List.of(편(12, 15), 편(18, 21)));

        assertEquals(RegionAccess.Status.AVAILABLE, 시간표붙음.status());
        assertEquals(2, 시간표붙음.departures().size());
    }

    /**
     * <b>빈 목록이면 올리지 않는다.</b>
     *
     * <p>"물어봤더니 없다" 와 "못 물었다" 는 여전히 다르고, 여기서는 그 둘을 가릴 근거가 없다 —
     * 조회창 밖이면 아예 안 묻는다.
     */
    @Test
    void 시간표가_비면_상태를_올리지_않는다() {
        RegionAccess 지점만 = RegionAccess.pointOnly(시외버스(읍내_터미널));

        assertEquals(RegionAccess.Status.POINT_ONLY, 지점만.withDepartures(List.of()).status());
    }

    /** 이미 다른 상태면 건드리지 않는다 — 올리는 것은 "아직 안 물었다" 뿐이다. */
    @Test
    void 미운행_상태는_시간표가_붙어도_그대로다() {
        RegionAccess 미운행 = RegionAccess.noServiceOnDate("서울", "완도", 읍내_터미널);

        assertEquals(RegionAccess.Status.NO_SERVICE_ON_DATE,
                미운행.withDepartures(List.of(편(12, 15))).status());
    }

    /**
     * 도착 시각을 <b>시간표 첫 편</b>에서도 안다(#422).
     *
     * <p>예전에는 열차의 {@code fastest} 만 봐서, 버스·여객선은 시간표가 있어도 소요시간으로만 답했다.
     * 그러면 첫날 재정렬이 실제 도착보다 이르거나 늦은 시각을 쓴다.
     */
    @Test
    void 열차가_아니어도_시간표에서_도착_시각을_안다() {
        RegionAccess 버스 = RegionAccess.pointOnly(시외버스(읍내_터미널))
                .withDepartures(List.of(편(12, 15), 편(18, 21)));

        assertEquals(LocalDate.of(2026, 9, 4).atTime(15, 40), 버스.arrivalAt().orElseThrow());
    }
}

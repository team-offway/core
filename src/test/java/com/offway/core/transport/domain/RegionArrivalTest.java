package com.offway.core.transport.domain;

import com.offway.core.common.geo.Coordinate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 도착 지점 선택 규칙(#97) — 코스 동선의 기준점을 무엇으로 잡을지가 여기서 갈린다. */
class RegionArrivalTest {

    private static final Coordinate 정선군청 = new Coordinate(37.3805, 128.6608);

    private static RegionArrival at(TransitMode mode, String name, double lat, double lng) {
        return new RegionArrival(mode, "CODE-" + name, name, new Coordinate(lat, lng));
    }

    @Test
    void 후보가_없으면_도착_지점을_모른다() {
        assertTrue(RegionArrival.nearestTo(정선군청).isEmpty());
    }

    @Test
    void 후보가_전부_null_이면_도착_지점을_모른다() {
        assertTrue(RegionArrival.nearestTo(정선군청, null, null).isEmpty());
    }

    @Test
    void 후보가_하나면_그것이_도착_지점이다() {
        RegionArrival only = at(TransitMode.INTERCITY_BUS, "정선", 37.3800, 128.6600);

        assertEquals(Optional.of(only), RegionArrival.nearestTo(정선군청, only));
    }

    @Test
    void 열차역이_멀고_시외버스_터미널이_가까우면_터미널로_내린다() {
        // 실제로 이런 지역이 여럿이다(양양·합천·태안 등) — 역까지 40㎞ 를 나가야 하는데
        // 터미널은 읍내에 있다. 수단으로 순위를 매기면 여기서 동선이 뒤집힌다.
        RegionArrival 먼_역 = at(TransitMode.TRAIN, "먼역", 37.7000, 128.6608);
        RegionArrival 가까운_터미널 = at(TransitMode.INTERCITY_BUS, "정선", 37.3810, 128.6610);

        Optional<RegionArrival> picked = RegionArrival.nearestTo(정선군청, 먼_역, 가까운_터미널);

        assertEquals(Optional.of(가까운_터미널), picked);
    }

    @Test
    void 열차역이_더_가까우면_역으로_내린다() {
        RegionArrival 가까운_역 = at(TransitMode.TRAIN, "정선역", 37.3806, 128.6609);
        RegionArrival 먼_터미널 = at(TransitMode.INTERCITY_BUS, "정선", 37.4500, 128.7200);

        assertEquals(Optional.of(가까운_역), RegionArrival.nearestTo(정선군청, 가까운_역, 먼_터미널));
    }

    @Test
    void 섬이라_항구만_있으면_배로_내린다() {
        // 울릉군 — 89곳 중 버스로 못 닿는 유일한 곳이다.
        Coordinate 울릉군청 = new Coordinate(37.4845, 130.9057);
        RegionArrival 도동항 = at(TransitMode.FERRY, "울릉_도동", 37.4838, 130.9053);

        assertEquals(Optional.of(도동항), RegionArrival.nearestTo(울릉군청, null, 도동항));
    }

    @Test
    void 터미널은_고속인지_시외인지를_스스로_안다() {
        Terminal 고속 = Terminal.builder()
                .code("NAEK010").name("서울경부").kind(BusTerminalKind.EXPRESS).coordinate(정선군청)
                .isTerminal(true).build();
        Terminal 시외 = Terminal.builder()
                .code("NAI2613201").name("정선").kind(BusTerminalKind.INTERCITY).coordinate(정선군청)
                .isTerminal(true).build();

        assertEquals(TransitMode.EXPRESS_BUS, RegionArrival.of(고속).mode());
        assertEquals(TransitMode.INTERCITY_BUS, RegionArrival.of(시외).mode());
    }

    @Test
    void 항구는_언제나_여객선이다() {
        Port 도동 = new Port("SEA43113", "울릉_도동", 정선군청);

        assertEquals(TransitMode.FERRY, RegionArrival.of(도동).mode());
    }

    @Test
    void 좌표_없는_도착_지점은_만들_수_없다() {
        assertThrows(NullPointerException.class, () -> new RegionArrival(TransitMode.TRAIN, "NAT1", "정선역", null));
    }
}

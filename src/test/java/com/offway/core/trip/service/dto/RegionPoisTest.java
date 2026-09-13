package com.offway.core.trip.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RegionPoisTest {

    private static PoiCandidate poi(String name) {
        return poiAt(name, 36.3, 128.6);
    }

    /**
     * 좌표가 겹치지 않게 흩어 둔다 — 같은 자리로 보면 중복 제거에 걸린다.
     *
     * <p><b>빌더로 만든다.</b> 위치 생성자로 두었더니 {@code PoiCandidate} 에 칸이 하나 늘 때마다
     * 여기가 깨졌다(#519 에서 실제로 깨졌다). 그 타입 자신의 주석이 빌더를 쓰라고 적어 둔 이유다.
     */
    private static PoiCandidate poiAt(String name, double lat, double lng) {
        return PoiCandidate.builder()
                .contentId(name)
                .contentTypeId(12)
                .title(name)
                .lat(lat)
                .lng(lng)
                .build();
    }

    private static List<PoiCandidate> pois(String prefix, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> poiAt(prefix + i, 36.3 + i * 0.01, 128.6 + i * 0.01))
                .toList();
    }

    @Test
    void 풀이_충분하면_보충하지_않는다() {
        RegionPois pois = new RegionPois(pois("s", 30), pois("f", 20), List.of(), pois("t", 10));

        RegionPois result = pois.supplementedWith(pois("보충", 5), pois("보충", 5), pois("보충", 5));

        assertEquals(30, result.sights().size());
        assertEquals(20, result.foods().size());
        assertEquals(10, result.stays().size());
        assertTrue(result.sights().stream().noneMatch(p -> p.title().startsWith("보충")));
    }

    /** 이 이슈의 출발점 — 숙박이 0건이면 "잘 곳 없는 2박3일" 이 나간다. */
    @Test
    void 숙박이_비면_보충으로_채운다() {
        RegionPois pois = new RegionPois(pois("s", 30), pois("f", 20), List.of(), List.of());

        RegionPois result = pois.supplementedWith(List.of(), List.of(), pois("숙소", 12));

        assertEquals(12, result.stays().size());
        assertTrue(result.stays().stream().allMatch(p -> p.title().startsWith("숙소")));
    }

    @Test
    void 부족한_풀만_채우고_기존_후보는_앞에_남긴다() {
        RegionPois pois = new RegionPois(pois("s", 30), pois("f", 2), List.of(), pois("t", 1));

        RegionPois result = pois.supplementedWith(pois("보충", 9), pois("보충", 9), pois("보충", 9));

        assertEquals(30, result.sights().size(), "충분한 볼거리는 그대로");
        assertEquals("f1", result.foods().getFirst().title(), "기존 후보가 앞");
        assertEquals(11, result.foods().size(), "기존 2 + 보충 9");
        assertEquals(10, result.stays().size(), "기존 1 + 보충 9");
    }

    @Test
    void 보충_후보가_없으면_원래대로_둔다() {
        RegionPois pois = new RegionPois(List.of(), List.of(), List.of(), List.of());

        RegionPois result = pois.supplementedWith(List.of(), List.of(), List.of());

        assertTrue(result.stays().isEmpty());
    }

    /** 같은 장소가 두 소스에 다 있으면 코스에 두 번 뜬다. 상호·좌표가 같으면 하나로 본다. */
    @Test
    void 이미_있는_장소는_보충에서_제외한다() {
        PoiCandidate duplicate = PoiCandidate.builder()
                .contentId("LIC-1")
                .contentTypeId(0)
                .title("올인모텔")
                .lat(36.3)
                .lng(128.6)
                .build();
        RegionPois pois = new RegionPois(List.of(), List.of(), List.of(), List.of(poi("올인모텔")));

        RegionPois result = pois.supplementedWith(List.of(), List.of(), List.of(duplicate));

        assertEquals(1, result.stays().size());
    }

    /** 소스마다 좌표 정밀도가 달라 같은 건물도 소수점이 어긋난다. 100m 안쪽이면 같은 곳으로 본다. */
    @Test
    void 좌표가_조금_달라도_같은_상호면_같은_곳으로_본다() {
        PoiCandidate nearlySame = PoiCandidate.builder()
                .contentId("LIC-1")
                .contentTypeId(0)
                .title("올 인 모텔")
                .lat(36.30004)
                .lng(128.60003)
                .build();
        RegionPois pois = new RegionPois(List.of(), List.of(), List.of(), List.of(poi("올인모텔")));

        RegionPois result = pois.supplementedWith(List.of(), List.of(), List.of(nearlySame));

        assertEquals(1, result.stays().size());
    }

    /**
     * 상호만으로 가르면 "○○식당" 본점과 2호점이 한 곳으로 접혀 풀이 덜 채워진다 — 부족해서 보충하는
     * 중인데 후보를 스스로 깎는 셈이다.
     */
    @Test
    void 상호가_같아도_위치가_다르면_둘_다_남긴다() {
        PoiCandidate branch = PoiCandidate.builder()
                .contentId("LIC-2")
                .contentTypeId(0)
                .title("대박집")
                .lat(36.42)
                .lng(128.78)
                .build();
        RegionPois pois = new RegionPois(List.of(), List.of(poi("대박집")), List.of(), List.of());

        RegionPois result = pois.supplementedWith(List.of(), List.of(branch), List.of());

        assertEquals(2, result.foods().size());
    }

    @Test
    void 어느_풀이든_부족한지_스스로_안다() {
        assertTrue(new RegionPois(List.of(), List.of(), List.of(), List.of()).needsSupplement());
        assertTrue(new RegionPois(pois("s", 30), pois("f", 20), List.of(), pois("t", 1)).needsSupplement());
        assertEquals(false, new RegionPois(pois("s", 30), pois("f", 20), List.of(), pois("t", 10)).needsSupplement());
    }
}

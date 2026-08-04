package com.offway.core.trip.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RegionPoisTest {

    private static PoiCandidate poi(String name) {
        return new PoiCandidate(name, 12, name, 36.3, 128.6, null, null, null);
    }

    private static List<PoiCandidate> pois(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> poi(prefix + i)).toList();
    }

    @Test
    void 풀이_충분하면_보충하지_않는다() {
        RegionPois pois = new RegionPois(pois("s", 30), pois("f", 20), pois("t", 10));

        RegionPois result = pois.supplementedWith(pois("보충", 5), pois("보충", 5), pois("보충", 5));

        assertEquals(30, result.sights().size());
        assertEquals(20, result.foods().size());
        assertEquals(10, result.stays().size());
        assertTrue(result.sights().stream().noneMatch(p -> p.title().startsWith("보충")));
    }

    /** 이 이슈의 출발점 — 숙박이 0건이면 "잘 곳 없는 2박3일" 이 나간다. */
    @Test
    void 숙박이_비면_보충으로_채운다() {
        RegionPois pois = new RegionPois(pois("s", 30), pois("f", 20), List.of());

        RegionPois result = pois.supplementedWith(List.of(), List.of(), pois("숙소", 12));

        assertEquals(12, result.stays().size());
        assertTrue(result.stays().stream().allMatch(p -> p.title().startsWith("숙소")));
    }

    @Test
    void 부족한_풀만_채우고_기존_후보는_앞에_남긴다() {
        RegionPois pois = new RegionPois(pois("s", 30), pois("f", 2), pois("t", 1));

        RegionPois result = pois.supplementedWith(pois("보충", 9), pois("보충", 9), pois("보충", 9));

        assertEquals(30, result.sights().size(), "충분한 볼거리는 그대로");
        assertEquals("f1", result.foods().getFirst().title(), "기존 후보가 앞");
        assertEquals(11, result.foods().size(), "기존 2 + 보충 9");
        assertEquals(10, result.stays().size(), "기존 1 + 보충 9");
    }

    @Test
    void 보충_후보가_없으면_원래대로_둔다() {
        RegionPois pois = new RegionPois(List.of(), List.of(), List.of());

        RegionPois result = pois.supplementedWith(List.of(), List.of(), List.of());

        assertTrue(result.stays().isEmpty());
    }

    /** 같은 장소가 두 소스에 다 있으면 코스에 두 번 뜬다. 상호·좌표가 같으면 하나로 본다. */
    @Test
    void 이미_있는_장소는_보충에서_제외한다() {
        PoiCandidate duplicate = new PoiCandidate("LIC-1", 0, "올인모텔", 36.3, 128.6, null, null, null);
        RegionPois pois = new RegionPois(List.of(), List.of(), List.of(poi("올인모텔")));

        RegionPois result = pois.supplementedWith(List.of(), List.of(), List.of(duplicate));

        assertEquals(1, result.stays().size());
    }

    @Test
    void 어느_풀이든_부족한지_스스로_안다() {
        assertTrue(new RegionPois(List.of(), List.of(), List.of()).needsSupplement());
        assertTrue(new RegionPois(pois("s", 30), pois("f", 20), pois("t", 1)).needsSupplement());
        assertEquals(false, new RegionPois(pois("s", 30), pois("f", 20), pois("t", 10)).needsSupplement());
    }
}

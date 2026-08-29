package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.service.GalleryRegionMatcher.RegionKey;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 갤러리 촬영 위치 원문 → 우리 89곳 매칭.
 *
 * <p>원문이 자유 텍스트라 표기가 제각각이다(실측 2026-08-09, 6,118건). <b>지명만으로 매칭하면 조용히
 * 틀린다</b> — 정규화 전에는 대구 남구가 104건으로 부풀었고(전국 남구의 합), 정규화 후 6건이 됐다.
 */
class GalleryRegionMatcherTest {

    private static RegionKey region(long id, String sido, String sigungu) {
        return new RegionKey(id, sido, sigungu);
    }

    /** 우리 89곳 중 이름이 겹치는 것들을 포함한 표본. */
    private static final List<RegionKey> REGIONS = List.of(
            region(1L, "부산광역시", "동구"),
            region(2L, "부산광역시", "서구"),
            region(3L, "대구광역시", "서구"),
            region(4L, "대구광역시", "남구"),
            region(5L, "강원특별자치도", "고성군"),
            region(6L, "경상남도", "고성군"),
            region(7L, "전남광주통합특별시", "완도군"),
            region(8L, "충청남도", "공주시"),
            region(9L, "전북특별자치도", "장수군"));

    private static final GalleryRegionMatcher MATCHER = new GalleryRegionMatcher(REGIONS);

    @ParameterizedTest
    @CsvSource({
        "'부산광역시 서구', 2",
        "'대구광역시 서구', 3",
        "'강원특별자치도 고성군', 5",
        "'경상남도 고성군', 6",
    })
    void 이름이_겹치는_시군구는_시도로_가른다(String location, long expectedId) {
        assertEquals(Optional.of(expectedId), MATCHER.match(location));
    }

    @ParameterizedTest
    @CsvSource({
        // 행정구역 개편 전후 표기가 섞여 온다 — 실측 기준 강원도 581건 / 강원특별자치도 198건.
        "'강원도 고성군', 5",
        "'전라북도 장수군', 9",
        "'전북 장수군', 9",
        // 전남과 광주를 묶은 표기(실측 711건). 우리 89곳에 광주 자치구가 없어 전남과만 매칭된다.
        "'전남광주통합특별시 완도군', 7",
        "'충남 공주시', 8",
    })
    void 옛_표기와_줄임말도_정본으로_읽는다(String location, long expectedId) {
        assertEquals(Optional.of(expectedId), MATCHER.match(location));
    }

    @Test
    void 시도가_오타여도_읽는다() {
        // 실측에 나온 오타 — 인청광역시·산광역시·전북특별자치도도.
        assertEquals(Optional.of(9L), MATCHER.match("전북특별자치도도 장수군"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"신승반점", "FNC", "전주식당", "대한민국(한국)", ""})
    void 시도_자리에_엉뚱한_값이_와도_지어내지_않는다(String location) {
        // 실측에 실제로 있던 값들. 못 읽으면 빈 값이어야 한다 — 틀린 지역에 붙이는 것보다 낫다.
        assertTrue(MATCHER.match(location).isEmpty());
    }

    @Test
    void 시도를_모르면_광역시_자치구는_붙이지_않는다() {
        // "동구" 는 전국에 여럿이라 시도 없이는 어느 곳인지 알 수 없다.
        assertTrue(MATCHER.match("동구 어딘가").isEmpty());
    }

    @Test
    void 시도를_몰라도_이름이_유일하면_붙인다() {
        // "완도군" 은 전국에서 유일해 시도가 없어도 안전하다. 실측 location 에 시군구만 오는 경우가 있다.
        assertEquals(Optional.of(7L), MATCHER.match("완도군 청산도"));
    }

    @Test
    void 위치가_없으면_빈_값이다() {
        assertTrue(MATCHER.match(null).isEmpty());
    }

    @Test
    void 시도는_읽혔지만_우리_지역이_아니면_붙이지_않는다() {
        // 갤러리 6,118건 중 4,300여 건이 우리 89곳 밖이다. 시도만 맞다고 아무 데나 붙이면 안 된다.
        assertTrue(MATCHER.match("부산광역시 해운대구").isEmpty());
        assertTrue(MATCHER.match("충청남도 천안시").isEmpty());
    }

    @Test
    void 시군구명이_여럿_들어_있으면_긴_이름을_고른다() {
        // 훑는 순서가 해시에 좌우되면 같은 원문이 실행마다 다른 지역에 붙을 수 있다.
        // 길이 내림차순으로 고정해 결과가 일정하고 최장 일치가 되게 한다.
        assertEquals(Optional.of(5L), MATCHER.match("강원특별자치도 고성군 거진읍"));
    }
}

package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 지역 장소의 불변식과 노출 판정(#304).
 *
 * <p>{@link RegionPoi#showable()} 이 이 클래스에서 제일 중요하다 — 이 값이 곧 "매력 포인트 장소" 에 실릴지를
 * 정한다. 잘못 판정하면 화면 가로 목록 중간에 회색 판이 낀다.
 */
class RegionPoiTest {

    private static final YearMonth BASE_YM = YearMonth.of(2026, 8);

    @Test
    void 사진이_있으면_카드로_내보낼_수_있다() {
        assertTrue(poi("http://tong.visitkorea.or.kr/a.jpg").showable());
    }

    /**
     * 사진이 없거나 <b>빈 문자열</b>이면 못 내보낸다.
     *
     * <p>빈 문자열을 따로 보는 이유는 외부가 실제로 그렇게 준다 — TourAPI 의 {@code firstImage} 는 사진이
     * 없을 때 {@code null} 이 아니라 빈 값으로 오는 경우가 있다. {@code null} 만 걸러내면 그것이 그대로
     * 화면에 나가 깨진 이미지가 된다.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 사진이_없거나_비면_카드로_못_내보낸다(String imageUrl) {
        assertFalse(poi(imageUrl).showable());
    }

    @Test
    void 기준월은_YYYYMM_여섯_자리다() {
        // 컬럼이 CHAR(6) 이라 자릿수가 어긋나면 조회가 조용히 0건이 된다.
        assertEquals("202608", RegionPoi.format(YearMonth.of(2026, 8)));
        assertEquals("202612", RegionPoi.format(YearMonth.of(2026, 12)));
    }

    @Test
    void 지역과_장소_식별자와_분류는_필수다() {
        assertThrows(NullPointerException.class, () -> builder().regionId(null).build());
        assertThrows(NullPointerException.class, () -> builder().contentId(null).build());
        assertThrows(NullPointerException.class, () -> builder().category(null).build());
        assertThrows(NullPointerException.class, () -> builder().title(null).build());
        assertThrows(NullPointerException.class, () -> builder().baseYm(null).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 장소_식별자와_이름은_빈_값일_수_없다(String blank) {
        assertThrows(IllegalArgumentException.class, () -> builder().contentId(blank).build());
        assertThrows(IllegalArgumentException.class, () -> builder().title(blank).build());
    }

    private static RegionPoi poi(String imageUrl) {
        return builder().imageUrl(imageUrl).build();
    }

    private static RegionPoi.RegionPoiBuilder builder() {
        return RegionPoi.builder()
                .regionId(1L)
                .contentId("2708108")
                .contentTypeId(12)
                .category(Category.SIGHT)
                .title("범일 이중섭거리")
                .baseYm(BASE_YM)
                .fetchedAt(LocalDateTime.of(2026, 8, 1, 4, 0));
    }
}

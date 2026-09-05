package com.offway.core.trip.infrastructure.festival.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.FestivalPlace;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 표준데이터 한 건이 <b>코스에 올릴 수 있는가</b>(#433).
 *
 * <p>446건 중 101건이 좌표가 없다. 그걸 그대로 담으면 엔티티 불변식이 예외를 던져 적재가 통째로
 * 멈추므로, 어댑터 경계에서 먼저 가른다.
 */
class StandardFestivalTest {

    private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 9, 5, 4, 50);

    private static StandardFestival 축제(Double lat, Double lng, LocalDate start, LocalDate end) {
        return new StandardFestival(
                "안동국제탈춤페스티벌",
                "안동시 탈춤공원 일원",
                "경상북도 안동시 육사로 239",
                "안동시",
                lat,
                lng,
                start,
                end,
                "탈춤 축제",
                "안동시",
                "054-000-0000",
                "https://example.kr");
    }

    private static StandardFestival 온전한축제() {
        return 축제(36.5684, 128.7294, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 4));
    }

    @Test
    void 이름과_기간과_좌표가_다_있으면_쓸_수_있다() {
        assertTrue(온전한축제().isUsable());
    }

    /**
     * <b>좌표 없는 것이 446건 중 101건이다.</b> 신안군은 25건 중 대부분이 그렇다. 주소는 있어 지오코딩
     * 여지가 있지만 별도 비용이라 지금은 뺀다.
     */
    @Test
    void 좌표가_없으면_쓸_수_없다() {
        assertFalse(축제(null, 128.7294, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 4)).isUsable());
        assertFalse(축제(36.5684, null, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 4)).isUsable());
    }

    @Test
    void 기간이_없으면_쓸_수_없다() {
        assertFalse(축제(36.5684, 128.7294, null, LocalDate.of(2026, 10, 4)).isUsable());
        assertFalse(축제(36.5684, 128.7294, LocalDate.of(2026, 9, 25), null).isUsable());
    }

    /** 뒤집힌 기간은 엔티티가 거절하므로 여기서 먼저 걸러 적재가 멈추지 않게 한다. */
    @Test
    void 기간이_뒤집혔으면_쓸_수_없다() {
        assertFalse(축제(36.5684, 128.7294, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 9, 25)).isUsable());
    }

    @Test
    void 이름이나_주소가_비면_쓸_수_없다() {
        StandardFestival 이름없음 = new StandardFestival(
                " ", null, "경상북도 안동시", "안동시", 36.5684, 128.7294,
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 4), null, null, null, null);
        StandardFestival 주소없음 = new StandardFestival(
                "축제", null, null, "안동시", 36.5684, 128.7294,
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 4), null, null, null, null);

        assertFalse(이름없음.isUsable());
        assertFalse(주소없음.isUsable());
    }

    @Test
    void 우리_도메인으로_옮긴다() {
        FestivalPlace place = 온전한축제().toPlace(16L, FETCHED_AT);

        assertEquals(16L, place.getRegionId());
        assertEquals("안동국제탈춤페스티벌", place.getName());
        assertEquals(LocalDate.of(2026, 9, 25), place.getEventStart());
        assertEquals(36.5684, place.getLat());
        assertTrue(place.isOpenOn(LocalDate.of(2026, 9, 30)));
    }
}

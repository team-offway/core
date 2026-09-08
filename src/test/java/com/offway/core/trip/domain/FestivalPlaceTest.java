package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 표준데이터 축제 한 건(#433).
 *
 * <p>여기서 잠그는 것은 <b>코스에 못 올릴 것이 들어오지 않는가</b> 다. 좌표 없는 축제는 동선에 못
 * 올리고, 기간이 뒤집힌 축제는 어떤 날짜에도 안 열리는 것으로 판정돼 있는 축제를 우리가 지운다.
 */
class FestivalPlaceTest {

    private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 9, 5, 4, 50);

    private static FestivalPlace.FestivalPlaceBuilder 기본() {
        return FestivalPlace.builder()
                .regionId(16L)
                .name("안동국제탈춤페스티벌")
                .address("경상북도 안동시 육사로 239")
                .lat(36.5684)
                .lng(128.7294)
                .eventStart(LocalDate.of(2026, 9, 25))
                .eventEnd(LocalDate.of(2026, 10, 4))
                .fetchedAt(FETCHED_AT);
    }

    @Test
    void 이름과_기간과_좌표가_있으면_만들어진다() {
        FestivalPlace festival = 기본().build();

        assertEquals("안동국제탈춤페스티벌", festival.getName());
        assertEquals(LocalDate.of(2026, 9, 25), festival.getEventStart());
    }

    /** <b>기간 당일을 포함한다.</b> 시작일에 도착하는 여행이 가장 흔하다. */
    @ParameterizedTest
    @CsvSource({
        "2026-09-24, false", // 하루 전
        "2026-09-25, true", // 시작일 당일
        "2026-09-30, true", // 중간
        "2026-10-04, true", // 종료일 당일
        "2026-10-05, false", // 하루 뒤
    })
    void 여행일에_열리는지_판정한다(LocalDate travelDate, boolean open) {
        assertEquals(open, 기본().build().isOpenOn(travelDate));
    }

    /**
     * <b>시작이 종료보다 늦으면 만들 수 없다.</b> 그대로 두면 어떤 날짜에도 열리지 않는 것으로 판정돼,
     * 있는 축제를 우리가 지우는 셈이 된다.
     */
    @Test
    void 시작일이_종료일보다_늦으면_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> 기본()
                .eventStart(LocalDate.of(2026, 10, 4))
                .eventEnd(LocalDate.of(2026, 9, 25))
                .build());
    }

    /** 같은 날 하루짜리 축제는 정상이다 — 시작과 종료가 같은 것은 뒤집힌 것이 아니다. */
    @Test
    void 하루짜리_축제는_정상이다() {
        FestivalPlace festival = 기본()
                .eventStart(LocalDate.of(2026, 9, 25))
                .eventEnd(LocalDate.of(2026, 9, 25))
                .build();

        assertTrue(festival.isOpenOn(LocalDate.of(2026, 9, 25)));
    }

    /**
     * <b>좌표가 대한민국 밖이면 거절한다.</b> 지자체가 잘못 올린 행이 섞이는데, 그대로 담으면 코스
     * 동선이 엉뚱한 곳을 지난다.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.0, 32.9, 39.1, 91.0})
    void 위도가_범위를_벗어나면_거절한다(double lat) {
        assertThrows(IllegalArgumentException.class, () -> 기본().lat(lat).build());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 123.9, 132.1})
    void 경도가_범위를_벗어나면_거절한다(double lng) {
        assertThrows(IllegalArgumentException.class, () -> 기본().lng(lng).build());
    }

    @Test
    void 이름이_비어_있으면_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> 기본().name(" ").build());
    }

    /**
     * 공개 식별자는 접두어로 갈린다 — TourAPI(숫자)·인허가({@code LIC-})·국가유산({@code HER-})과
     * 섞여 한 응답에 나가기 때문이다.
     */
    @Test
    void 공개_식별자는_접두어를_달고_되돌아온다() {
        assertEquals("FST-42", FestivalPlace.publicId(42L));
        assertEquals(42L, FestivalPlace.parsePublicId("FST-42").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HER-42", "LIC-42", "2708108", "FST-abc", "FST-", ""})
    void 우리_식별자가_아니면_비어_있다(String publicId) {
        assertTrue(FestivalPlace.parsePublicId(publicId).isEmpty());
    }

    @Test
    void null_식별자도_비어_있다() {
        assertTrue(FestivalPlace.parsePublicId(null).isEmpty());
    }

    /**
     * <b>선택 값이 길다고 축제를 버리지 않는다.</b> 전화번호 하나 때문에 이름·기간·좌표까지 잃으면,
     * 정작 코스에 필요한 것을 못 쓰게 된다.
     */
    @Test
    void 선택_값이_너무_길면_잘라_담는다() {
        String 아주긴전화 = "0".repeat(200);

        FestivalPlace festival = 기본().tel(아주긴전화).build();

        assertEquals(50, festival.getTel().length());
    }

    @Test
    void 빈_선택_값은_null_로_담는다() {
        FestivalPlace festival = 기본().venue("  ").host("").tel(null).build();

        assertEquals(null, festival.getVenue());
        assertEquals(null, festival.getHost());
        assertEquals(null, festival.getTel());
    }

    /** 이름이 정확히 상한이면 통과하고, 넘으면 거절한다 — 필수값은 자르지 않는다. */
    @Test
    void 이름이_상한을_넘으면_거절한다() {
        assertFalse(기본().name("가".repeat(200)).build().getName().isBlank());
        assertThrows(IllegalArgumentException.class, () -> 기본().name("가".repeat(201)).build());
    }
}

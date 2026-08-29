package com.offway.core.curation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SurfaceTest {

    @ParameterizedTest
    @CsvSource({
        "HOME, HOME",
        "'HOME,REGION', HOME",
        "'REGION,HOME', HOME",
        "'HOME, REGION', REGION",
        "'HOME,REGION,COURSE,POI', POI",
    })
    void 저장된_문자열에서_그_면을_읽어낸다(String stored, Surface expected) {
        assertTrue(Surface.parse(stored).contains(expected));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ",", ",,"})
    void 비어_있거나_구분자만_있으면_아무_면도_아니다(String stored) {
        assertEquals(Set.of(), Surface.parse(stored));
    }

    /**
     * 상수명을 바꾸거나 지우면 기존 행과 어긋나는데, 그때 예외를 던지면 그 행 하나 때문에 <b>화면이 통째로
     * 500</b> 이 된다. 링크 하나가 안 보이는 편이 낫다.
     */
    @Test
    void 모르는_이름은_던지지_않고_건너뛴다() {
        assertEquals(Set.of(Surface.HOME, Surface.POI), Surface.parse("HOME,ADMIN_ONLY,POI"));
    }

    @Test
    void 아는_이름이_하나도_없으면_빈_집합이다() {
        assertEquals(Set.of(), Surface.parse("WIDGET,WATCH"));
    }

    /** 소문자·다른 표기는 상수명이 아니다 — DB 에 들어간 값과 정확히 같아야 한다. */
    @Test
    void 대소문자가_다르면_모르는_이름이다() {
        assertEquals(Set.of(), Surface.parse("home"));
    }

    /**
     * 넣은 순서가 달라도 같은 문자열이 나와야 한다. 아니면 같은 내용인데 저장값이 갈려 어드민 diff 가
     * 의미 없이 뜨고, 응답 목록도 요청마다 뒤섞인다.
     */
    @Test
    void 같은_집합이면_넣은_순서와_무관하게_같은_문자열이_된다() {
        Set<Surface> reversed = new LinkedHashSet<>();
        reversed.add(Surface.POI);
        reversed.add(Surface.HOME);

        assertEquals("HOME,POI", Surface.join(reversed));
        assertEquals(Surface.join(Set.of(Surface.HOME, Surface.POI)), Surface.join(reversed));
    }

    @Test
    void 저장했다_읽으면_같은_집합으로_돌아온다() {
        Set<Surface> all = Set.of(Surface.HOME, Surface.REGION, Surface.COURSE, Surface.POI);

        assertEquals(all, Surface.parse(Surface.join(all)));
    }
}

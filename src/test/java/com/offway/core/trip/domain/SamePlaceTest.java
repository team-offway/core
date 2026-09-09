package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 같은 장소를 한 번만 세는가(#548).
 *
 * <p>양쪽으로 나빠진다 — 느슨하면 "○○식당" 본점과 2호점이 한 곳으로 접혀 후보 풀이 얇아지고, 빡빡하면
 * 같은 곳이 코스에 두 번 뜬다.
 */
class SamePlaceTest {

    private static final String TOUR = "129886";
    private static final String OTHER_TOUR = "129887";
    private static final String LICENSED = "LIC-310273";
    private static final String OTHER_LICENSED = "LIC-310274";

    @Test
    void 출처가_다르면_727m_도_같은_장소다() {
        // 이 값이 이 클래스의 이유다 — 횡성문화원이 관광 API 와 인허가에서 727m 어긋나 코스에 두 번 떴다.
        assertTrue(SamePlace.is(
                "횡성문화원", LICENSED, 37.48655, 127.98393,
                "횡성문화원", TOUR, 37.49147, 127.97850));
    }

    @Test
    void 출처가_다르면_1km_를_넘을_때_갈라진다() {
        // negative control — 상한이 없으면 "금강식당" 두 지점(4.2㎞)까지 한 곳으로 접힌다.
        assertFalse(SamePlace.is(
                "금강식당", LICENSED, 36.0000, 127.0000,
                "금강식당", TOUR, 36.0500, 127.0000));
    }

    @Test
    void 출처가_같으면_200m_만_같은_장소다() {
        // 인허가끼리 이름이 겹치는 쌍은 40% 가 3㎞ 이상 떨어져 있다 — 대부분 같은 상호의 다른 지점이다.
        assertTrue(SamePlace.is(
                "진미당제과", LICENSED, 35.7260, 128.2620,
                "진미당제과", OTHER_LICENSED, 35.7262, 128.2622));
    }

    @Test
    void 출처가_같으면_727m_는_다른_장소다() {
        // 출처가 갈릴 때와 같은 거리인데 판정이 반대다. 그 차이가 이 클래스가 존재하는 이유다.
        assertFalse(SamePlace.is(
                "복실이네 맛집", LICENSED, 37.48655, 127.98393,
                "복실이네 맛집", OTHER_LICENSED, 37.49147, 127.97850));
    }

    @Test
    void 관광_API_끼리도_좁은_기준을_쓴다() {
        assertFalse(SamePlace.is(
                "○○식당", TOUR, 37.48655, 127.98393,
                "○○식당", OTHER_TOUR, 37.49147, 127.97850));
    }

    @Test
    void 이름이_다르면_아무리_가까워도_다른_장소다() {
        // 같은 건물의 다른 가게다. 좌표만 보면 상가 전체가 한 곳으로 접힌다.
        assertFalse(SamePlace.is(
                "1층 카페", LICENSED, 37.5000, 127.0000,
                "2층 식당", TOUR, 37.5000, 127.0000));
    }

    @Test
    void 띄어쓰기와_대소문자는_무시한다() {
        // 소스마다 표기가 달라 그대로 비교하면 같은 곳을 다른 곳으로 센다.
        assertTrue(SamePlace.is(
                "Cafe Olle", LICENSED, 37.5000, 127.0000,
                "cafeolle", TOUR, 37.5001, 127.0001));
    }

    @Test
    void 이름이_비면_접지_않는다() {
        // 빈 이름끼리 같다고 보면 이름 없는 후보가 통째로 하나로 접힌다.
        assertFalse(SamePlace.is(
                "", LICENSED, 37.5000, 127.0000,
                "", TOUR, 37.5000, 127.0000));
        assertFalse(SamePlace.is(
                null, LICENSED, 37.5000, 127.0000,
                null, TOUR, 37.5000, 127.0000));
    }

    @Test
    void 좌표가_이상하면_접지_않는다() {
        // NaN 을 Coordinate 에 넣으면 예외가 나고, 후보 하나가 풀 전체를 막는다.
        assertFalse(SamePlace.is(
                "담양호", TOUR, Double.NaN, 127.0000,
                "담양호", LICENSED, 35.3000, 127.0000));
        assertFalse(SamePlace.is(
                "담양호", TOUR, 200.0, 127.0000,
                "담양호", LICENSED, 35.3000, 127.0000));
    }
}

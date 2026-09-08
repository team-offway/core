package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("중심관광지 인기 순위 매칭")
class PopularityTest {

    private static final double SEOUL_LAT = 37.5;
    private static final double SEOUL_LNG = 127.0;

    private static HubAttraction hub(String name, int rank, Double lat, Double lng) {
        return HubAttraction.builder()
                .regionId(1L)
                .baseMonth(YearMonth.of(2026, 8))
                .hubRank(rank)
                .hubCode("HUB-" + rank)
                .name(name)
                .categoryLarge("관광지")
                .lat(lat)
                .lng(lng)
                .build();
    }

    @Test
    void 이름이_같으면_좌표가_멀어도_순위를_준다() {
        Popularity popularity = Popularity.of(List.of(hub("꽃지해수욕장", 1, SEOUL_LAT, SEOUL_LNG)));

        // 같은 이름인데 좌표가 20㎞ 넘게 떨어져 있다 — 출처마다 대표 좌표를 다르게 잡는 일이 흔하다.
        assertEquals(OptionalInt.of(1), popularity.rankOf("꽃지해수욕장", 37.7, 127.0));
    }

    @ParameterizedTest(name = "{0} 은 같은 이름으로 본다")
    @CsvSource({"꽃지 해수욕장", "꽃지·해수욕장", "꽃지(해수욕장)"})
    void 공백과_기호만_다르면_같은_이름으로_본다(String title) {
        Popularity popularity = Popularity.of(List.of(hub("꽃지해수욕장", 3, SEOUL_LAT, SEOUL_LNG)));

        // 좌표는 멀리 둔다 — 이름만으로 걸리는지를 봐야 해서다.
        assertEquals(OptionalInt.of(3), popularity.rankOf(title, 38.5, 128.0));
    }

    @ParameterizedTest(name = "{0} 은 이름으로는 안 걸린다")
    @CsvSource({"완도타워모노레일", "완도타워 주차장", "제2완도타워"})
    void 말이_덧붙으면_다른_이름으로_본다(String title) {
        // **부분 문자열로 느슨하게 맞추지 않는다.** 실제 완도 데이터에 `완도타워`(1위)와
        // `완도타워모노레일`(10위)이 **각각** 있다. 포함 관계로 맞추면 둘이 서로를 삼켜, 한쪽의 인기가
        // 엉뚱한 곳에 붙는다. 이름이 어긋나도 같은 자리면 아래 좌표 규칙이 받는다.
        Popularity popularity = Popularity.of(List.of(hub("완도타워", 1, SEOUL_LAT, SEOUL_LNG)));

        assertEquals(OptionalInt.empty(), popularity.rankOf(title, 38.5, 128.0));
    }

    @Test
    void 이름이_달라도_300m_안이면_같은_곳으로_본다() {
        Popularity popularity = Popularity.of(List.of(hub("완도타워", 2, SEOUL_LAT, SEOUL_LNG)));

        // 위도 0.002도 = 약 222m.
        assertEquals(OptionalInt.of(2), popularity.rankOf("완도타워 전망대", SEOUL_LAT + 0.002, SEOUL_LNG));
    }

    @Test
    void 이름이_다르고_300m_밖이면_순위를_주지_않는다() {
        Popularity popularity = Popularity.of(List.of(hub("완도타워", 2, SEOUL_LAT, SEOUL_LNG)));

        // 위도 0.01도 = 약 1.1㎞. 여기까지 같다고 보면 매칭이 아니라 추측이 된다.
        assertEquals(OptionalInt.empty(), popularity.rankOf("장보고기념관", SEOUL_LAT + 0.01, SEOUL_LNG));
    }

    @Test
    void 여러_중심에_걸리면_가장_높은_순위를_준다() {
        Popularity popularity = Popularity.of(List.of(
                hub("완도타워", 9, SEOUL_LAT, SEOUL_LNG),
                hub("완도타워모노레일", 4, SEOUL_LAT + 0.001, SEOUL_LNG)));

        // 이름으로 9위에, 좌표로 4위에 걸린다 — 낮은 쪽을 주면 인기 있는 곳이 뒤로 밀린다.
        assertEquals(OptionalInt.of(4), popularity.rankOf("완도타워", SEOUL_LAT, SEOUL_LNG));
    }

    @Test
    void 이름이_비어도_다른_빈_이름과_묶이지_않는다() {
        // 정규화하면 기호만 있는 이름은 빈 문자열이 된다. 그때 빈 이름끼리 같다고 보면
        // 좌표가 아무리 멀어도 통째로 묶인다.
        Popularity popularity = Popularity.of(List.of(hub("...", 1, SEOUL_LAT, SEOUL_LNG)));

        assertEquals(OptionalInt.empty(), popularity.rankOf("!!!", 38.5, 128.0));
    }

    @Test
    void 좌표가_없는_중심은_버린다() {
        Popularity popularity = Popularity.of(List.of(hub("이름만있는곳", 1, null, null)));

        assertTrue(popularity.isEmpty());
        assertEquals(OptionalInt.empty(), popularity.rankOf("이름만있는곳", SEOUL_LAT, SEOUL_LNG));
    }

    @Test
    void 중심이_없으면_비어있다() {
        assertTrue(Popularity.none().isEmpty());
        assertTrue(Popularity.of(List.of()).isEmpty());
        assertFalse(Popularity.of(List.of(hub("완도타워", 1, SEOUL_LAT, SEOUL_LNG))).isEmpty());
    }
}

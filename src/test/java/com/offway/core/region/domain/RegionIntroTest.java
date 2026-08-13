package com.offway.core.region.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 지역 한 줄 소개(#140) — <b>지어내지 않고 사실만 조합하는가</b>.
 *
 * <p>감성 카피를 주는 외부 출처가 없어, 그 지역에 실제로 있는 것의 이름을 쓴다. 대신 문장이 어색하면
 * 화면이 망가지므로 조사·길이를 다듬는다.
 */
class RegionIntroTest {

    @Test
    void 두_곳이면_둘을_잇는다() {
        RegionIntro intro = RegionIntro.of("평창군", List.of("평창 월정사 팔각 구층석탑", "평창 오대산사고"));

        assertEquals("월정사 팔각 구층석탑과 오대산사고가 있는 곳", intro.text());
    }

    @Test
    void 한_곳이면_그것만_쓴다() {
        assertEquals("현등사 극락전이 있는 곳", RegionIntro.of("가평군", List.of("가평 현등사 극락전")).text());
    }

    @ParameterizedTest
    @CsvSource({
            "오대산사고, 오대산사고가 있는 곳",
            "극락전,    극락전이 있는 곳",
            "화양구곡,  화양구곡이 있는 곳",
    })
    void 조사를_받침으로_고른다(String name, String expected) {
        // `와(과)` 처럼 둘 다 적으면 화면이 어색해진다. 한글은 종성으로 결정되므로 계산할 수 있다.
        assertEquals(expected, RegionIntro.of("어디군", List.of(name)).text());
    }

    @Test
    void 지역명_접두어를_뗀다() {
        // 카드에 지역명이 이미 있는데 문구에서 또 반복하면 "의성군 · 경북 / 의성 탑리리…" 가 된다.
        assertEquals("탑리리 오층석탑이 있는 곳", RegionIntro.of("의성군", List.of("의성 탑리리 오층석탑")).text());
    }

    @Test
    void 카드에_안_맞는_이름은_뺀다() {
        // `제2로 직봉 - 의성 계란현 봉수 유적` 이 들어가면 소개가 아니라 목록처럼 읽힌다.
        RegionIntro intro = RegionIntro.of("의성군",
                List.of("의성 탑리리 오층석탑", "제2로 직봉 - 의성 계란현 봉수 유적"));

        assertEquals("탑리리 오층석탑이 있는 곳", intro.text());
    }

    @Test
    void 재료가_없으면_소개도_없다() {
        // 어색한 문구보다 없는 편이 낫다 — 응답에서 필드 자체가 사라진다.
        assertTrue(RegionIntro.of("어디군", List.of()).isEmpty());
        assertTrue(RegionIntro.of("어디군", List.of("제1로 직봉 - 아주 긴 이름의 어떤 유적지 이름")).isEmpty());
    }

    @Test
    void 같은_이름은_한_번만_쓴다() {
        assertEquals("같은절이 있는 곳", RegionIntro.of("어디군", List.of("같은절", "같은절")).text());
    }
}

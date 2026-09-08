package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 상호와 분류에서 <b>무슨 음식인가</b>를 읽는다.
 *
 * <p>실측(2026-09-08 · 89곳 전수)이 이 설계를 정했다 — TourAPI 분류는 89곳 중 84곳의 대표가 한식이고
 * 인허가 분류도 {@code KOREAN} 이 52% 라, 분류만으로는 사용자가 느끼는 겹침(한우×2·꽃게×2)이 안 갈린다.
 */
class FoodTasteTest {

    /** 실제 코스에서 겹쳤던 조합들 — 이게 안 걸리면 이 기능은 아무 일도 안 한다. */
    @ParameterizedTest
    @CsvSource({
            "횡성한우마을, 횡성순한우",
            "꽃지원조꽃게집, 원조꽃게장,",
            "용둔막국수, 봉평메밀촌",
            "공지천닭갈비, 원조통닭",
    })
    void 실제로_겹쳤던_조합을_같은_음식으로_읽는다(String one, String other) {
        assertTrue(FoodTaste.same(one, null, other, null),
                one + " 와 " + other + " 가 다른 음식으로 읽힌다");
    }

    @ParameterizedTest
    @CsvSource({
            "횡성한우마을, 용둔막국수",
            "꽃지원조꽃게집, 파파스테이크",
            "밀리앤코카페, 청학동한식전문점",
    })
    void 다른_음식은_다르게_읽는다(String one, String other) {
        assertFalse(FoodTaste.same(one, null, other, null));
    }

    /**
     * <b>긴 말이 먼저다.</b> 뒤집히면 닭갈비가 갈비(소)로, 막국수가 국수로 뭉쳐 엉뚱한 것이 겹친 것이 된다.
     */
    @Test
    void 닭갈비는_갈비가_아니고_막국수는_국수가_아니다() {
        assertEquals(Optional.of(FoodTaste.CHICKEN), FoodTaste.of("공지천닭갈비"));
        assertEquals(Optional.of(FoodTaste.BEEF), FoodTaste.of("서울갈비"));
        assertEquals(Optional.of(FoodTaste.BUCKWHEAT), FoodTaste.of("용둔막국수"));
        assertEquals(Optional.of(FoodTaste.NOODLE), FoodTaste.of("또바기손칼국수"));
    }

    /**
     * <b>모르면 다른 음식으로 본다.</b> 상호로 읽히는 것이 33% 뿐이라, 확신 없이 같다고 하면
     * 사진 있는 좋은 후보가 근거 없이 밀려난다.
     */
    @Test
    void 상호에서_못_읽으면_같다고_하지_않는다() {
        assertTrue(FoodTaste.of("느티나무가든").isEmpty());
        assertFalse(FoodTaste.same("느티나무가든", null, "그린가든", null),
                "둘 다 못 읽었는데 같다고 하면 이름만 비슷한 다른 집이 지워진다");
    }

    /** 상호를 못 읽으면 <b>이미 풀린 분류</b>를 쓴다. */
    @Test
    void 상호를_못_읽으면_분류로_갈린다() {
        assertTrue(FoodTaste.same("바다마을", FoodTaste.RAW_FISH, "포구식당", FoodTaste.RAW_FISH));
        assertFalse(FoodTaste.same("느티나무가든", null, "그린가든", null),
                "한식은 분류가 아무 말도 안 해 준다 — 그때 같다고 하면 멀쩡한 후보가 지워진다");
        assertFalse(FoodTaste.same("바다마을", FoodTaste.RAW_FISH, "면옥", FoodTaste.NOODLE));
    }

    /** 분류보다 상호가 먼저다 — 분류를 먼저 보면 한식으로 풀린 한우집이 상호까지 못 간다. */
    @Test
    void 분류보다_상호가_먼저다() {
        assertEquals(Optional.of(FoodTaste.BEEF), FoodTaste.of("횡성한우마을", null));
        assertTrue(FoodTaste.same("횡성한우마을", null, "횡성순한우", null));
    }

    /**
     * <b>코드 해석은 각 출처가 소유한다.</b> 도메인이 {@code A05020200} 을 직접 읽으면 외부 API 세부에
     * 묶인다(CodeRabbit #521 리뷰).
     */
    @Test
    void 관광API_코드는_어댑터가_옮긴다() {
        assertEquals(Optional.of(FoodTaste.CHINESE),
                TourPoi.builder().cat3("A05020400").build().foodTaste());
        assertEquals(Optional.of(FoodTaste.CAFE),
                TourPoi.builder().cat3("A05020900").build().foodTaste());
        assertTrue(TourPoi.builder().cat3("A05020100").build().foodTaste().isEmpty(),
                "한식은 89곳 중 84곳의 대표라 갈리는 것이 없다");
    }

    /** 인허가 분류도 자기가 옮긴다 — 한 칸이 큰 것은 아무 말도 안 한다. */
    @Test
    void 인허가_분류는_자기가_옮긴다() {
        assertEquals(Optional.of(FoodTaste.RAW_FISH), PlaceCategory.SEAFOOD.taste());
        assertEquals(Optional.of(FoodTaste.CAFE), PlaceCategory.COFFEE.taste());
        assertTrue(PlaceCategory.KOREAN.taste().isEmpty(), "전체의 52% 라 근거가 못 된다");
        assertTrue(PlaceCategory.RESTAURANT.taste().isEmpty());
    }
}

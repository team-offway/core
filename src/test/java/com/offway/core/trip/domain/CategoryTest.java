package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"NA", "HS", "VE", "LS", "EV", "AC", "EX", "FD", "ZZ"})
    void ALL은_어떤_코드든_포함한다(String code) {
        assertTrue(Category.ALL.includes(code));
    }

    @Test
    void SIGHT는_자연역사탈것행사쇼핑_묶음만_포함한다() {
        assertTrue(Category.SIGHT.includes("NA"));
        assertTrue(Category.SIGHT.includes("HS"));
        assertTrue(Category.SIGHT.includes("VE"));
        assertTrue(Category.SIGHT.includes("EV"));
        // 쇼핑을 여기 넣었다(#304). 칩은 넷으로 고정이라 자리를 못 주는데, 빼면 저장조차 안 돼
        // 전체 탭에서도 사라진다 — 실측에서 전 지역 33건이 그렇게 버려지고 있었다.
        assertTrue(Category.SIGHT.includes("SH"));
        // 레포츠는 체험으로 옮겼다(#304). 관광지에 두면 체험 칩이 사실상 비었다.
        assertFalse(Category.SIGHT.includes("LS"));
        assertFalse(Category.SIGHT.includes("AC"));
        assertFalse(Category.SIGHT.includes("EX"));
        assertFalse(Category.SIGHT.includes("FD"));
    }

    /**
     * 체험은 <b>둘</b>을 든다 — 체험(EX)과 레포츠(LS).
     *
     * <p>실측(2026-08-21, 89곳 전수)에서 순수 EX 만으로는 체험 칩이 얇았다. 레포츠를 옮기니 전 지역
     * 합계가 412 → 581건(+41%)이 됐고, 정선군은 8 → 17건이 되어 레일바이크·짚와이어·스키장이
     * 비로소 체험으로 잡힌다.
     */
    @Test
    void EXPERIENCE는_체험과_레포츠를_함께_든다() {
        assertTrue(Category.EXPERIENCE.includes("EX"));
        assertTrue(Category.EXPERIENCE.includes("LS"));
        assertFalse(Category.EXPERIENCE.includes("NA"));
        assertFalse(Category.EXPERIENCE.includes("AC"));
    }

    @Test
    void STAY_와_FOOD는_각자_한_코드만_포함한다() {
        assertTrue(Category.STAY.includes("AC"));
        assertFalse(Category.STAY.includes("NA"));
        assertTrue(Category.FOOD.includes("FD"));
        assertFalse(Category.FOOD.includes("NA"));
    }

    @Test
    void lclsSystm1Codes는_필터에_실을_코드묶음을_주고_ALL은_빈집합이다() {
        assertEquals(Set.of("NA", "HS", "VE", "EV", "SH"), Category.SIGHT.lclsSystm1Codes());
        assertEquals(Set.of("EX", "LS"), Category.EXPERIENCE.lclsSystm1Codes());
        assertEquals(Set.of("AC"), Category.STAY.lclsSystm1Codes());
        assertTrue(Category.ALL.lclsSystm1Codes().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"NA,SIGHT", "HS,SIGHT", "EV,SIGHT", "SH,SIGHT", "AC,STAY", "EX,EXPERIENCE", "LS,EXPERIENCE", "FD,FOOD"})
    void fromLclsSystm1은_코드를_소유한_구체칩으로_되돌린다(String code, Category expected) {
        assertEquals(Optional.of(expected), Category.fromLclsSystm1(code));
    }

    @Test
    void fromLclsSystm1은_미지의코드나_null이면_빈Optional이고_ALL로는_매핑하지_않는다() {
        assertEquals(Optional.empty(), Category.fromLclsSystm1("ZZ"));
        assertEquals(Optional.empty(), Category.fromLclsSystm1(null));
    }

    // ── 부제 조합(#305) — 카드에서 장소명 아래 한 줄

    /**
     * 맛집은 <b>대표메뉴</b>가 앞이다.
     *
     * <p>시안은 {@code 1인 9,000원} 이었는데 TourAPI 음식점에 가격 필드가 아예 없다. 대표메뉴는 표본 30건
     * 전부 채워져 있어 그것으로 대체했다.
     */
    @Test
    void 맛집_부제는_대표메뉴다() {
        PoiIntro intro = PoiIntro.builder().signatureMenu("갈치조림정식").useTime("10:00 ~ 19:00").build();

        assertEquals(Optional.of("갈치조림정식"), Category.FOOD.subtitle(intro, "바다 앞 노포"));
    }

    /** 대표메뉴가 없으면 영업시간으로 내려간다 — 사슬의 두 번째다. */
    @Test
    void 맛집은_대표메뉴가_없으면_영업시간을_쓴다() {
        PoiIntro intro = PoiIntro.builder().useTime("10:00 ~ 19:00").build();

        assertEquals(Optional.of("10:00 ~ 19:00"), Category.FOOD.subtitle(intro, null));
    }

    /**
     * 숙박은 객실 수와 입실 시각을 <b>한 줄로 잇는다</b>.
     *
     * <p>시각만 있으면 무엇의 시각인지 화면에서 알 수 없어 "입실" 을 붙인다.
     */
    @Test
    void 숙박_부제는_객실수와_입실시각을_잇는다() {
        PoiIntro intro = PoiIntro.builder().roomCount("13실").checkIn("15:00").build();

        assertEquals(Optional.of("13실 · 15:00 입실"), Category.STAY.subtitle(intro, null));
    }

    /**
     * 객실 수가 <b>숫자만</b> 오면 단위를 붙인다.
     *
     * <p>외부가 {@code 13} 으로도 {@code 13실} 로도 준다. 그대로 내리면 카드마다 표기가 달라진다.
     */
    @Test
    void 객실수가_숫자만이면_단위를_붙인다() {
        PoiIntro intro = PoiIntro.builder().roomCount("13").build();

        assertEquals(Optional.of("13실"), Category.STAY.subtitle(intro, null));
    }

    /** 한쪽만 있으면 그것만 쓴다 — 가운뎃점이 홀로 남으면 안 된다. */
    @Test
    void 숙박은_입실시각만_있으면_그것만_쓴다() {
        PoiIntro intro = PoiIntro.builder().checkIn("15:00").build();

        assertEquals(Optional.of("15:00 입실"), Category.STAY.subtitle(intro, null));
    }

    /** 관광지는 캐치프레이즈가 앞이다 — 상세를 안 불러도 되는 유일한 칩이다. */
    @Test
    void 관광지_부제는_캐치프레이즈다() {
        PoiIntro intro = PoiIntro.builder().fee("무료").build();

        assertEquals(Optional.of("폐광촌에서 다시 태어난 마을"),
                Category.SIGHT.subtitle(intro, "폐광촌에서 다시 태어난 마을"));
    }

    /**
     * <b>체험은 요금이 앞이고 없으면 캐치프레이즈다.</b>
     *
     * <p>이 칩에 묶인 레포츠(LS)는 상세가 빈 응답으로 와서(20건 중 19건) {@code intro} 자체가 없다.
     * 그때 사슬이 캐치프레이즈로 메운다.
     */
    @Test
    void 체험은_요금이_없으면_캐치프레이즈로_메운다() {
        assertEquals(Optional.of("래프팅으로 여는 여름"),
                Category.EXPERIENCE.subtitle(null, "래프팅으로 여는 여름"));
    }

    /**
     * <b>체험은 체험안내가 앞이다.</b>
     *
     * <p>요금으로 짰다가 바꿨다 — 실측 37건 중 요금이 있는 것이 0건이었다. 체험은 콘텐츠 타입
     * 12(관광지)로 와서 요금 칸이 아예 없다.
     */
    @Test
    void 체험_부제는_체험안내가_앞이다() {
        PoiIntro intro = PoiIntro.builder()
                .experienceGuide("차조강정 체험 / 사과 향기청 체험 / 목공예 체험 등")
                .fee("성인 12,000원")
                .build();

        assertEquals(Optional.of("차조강정 체험 / 사과 향기청 체험 / 목공예 체험 등"),
                Category.EXPERIENCE.subtitle(intro, "래프팅으로 여는 여름"));
    }

    /** 요금은 뒤로 물렸을 뿐 살아 있다 — 문화시설·레포츠가 이 칩에 섞여 오면 그때 걸린다. */
    @Test
    void 체험은_체험안내가_없으면_요금을_쓴다() {
        PoiIntro intro = PoiIntro.builder().fee("성인 12,000원").build();

        assertEquals(Optional.of("성인 12,000원"), Category.EXPERIENCE.subtitle(intro, "래프팅으로 여는 여름"));
    }

    /**
     * <b>재료가 없으면 지어내지 않는다.</b>
     *
     * <p>빈 값이면 앱이 그 줄을 접는다. 실측상 숙박은 캠핑장이, 체험은 레포츠가 이 자리에 온다.
     */
    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"SIGHT", "STAY", "EXPERIENCE", "FOOD"})
    void 재료가_없으면_부제를_비운다(Category category) {
        assertEquals(Optional.empty(), category.subtitle(null, null));
        assertEquals(Optional.empty(), category.subtitle(PoiIntro.builder().build(), null));
    }

    /** 공백만 있는 값은 없는 것으로 본다 — 외부가 빈 문자열을 실어 보낸다. */
    @Test
    void 공백만_있는_값은_없는_것으로_본다() {
        PoiIntro intro = PoiIntro.builder().signatureMenu("   ").useTime("").build();

        assertEquals(Optional.empty(), Category.FOOD.subtitle(intro, null));
    }

    /** {@code ALL} 은 필터가 아니라 전체 표지라 부제를 갖지 않는다. */
    @Test
    void ALL은_부제를_갖지_않는다() {
        PoiIntro intro = PoiIntro.builder().signatureMenu("갈치조림정식").build();

        assertEquals(Optional.empty(), Category.ALL.subtitle(intro, "무엇이든"));
    }
}

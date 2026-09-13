package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 반려동반 장소 단위 테스트(#566).
 *
 * <p>핵심은 <b>"전 구역" 판정</b>이다. 실측에서 절반이 "일부구역" 이었으므로, 이 판정이 틀리면 사용자가
 * 못 들어가는 구역이 있는 곳을 전 구역으로 믿고 간다.
 */
class PetFriendlyPlaceTest {

    private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 9, 13, 5, 10);

    private static PetFriendlyPlace.PetFriendlyPlaceBuilder place() {
        return PetFriendlyPlace.builder()
                .contentId("127311")
                .regionId(1L)
                .name("갈음이해수욕장")
                .fetchedAt(FETCHED_AT);
    }

    @Test
    void 전구역_동반가능이면_전_구역이다() {
        assertTrue(place().accompanyArea("전구역 동반가능").build().allowsWholeArea());
    }

    @ParameterizedTest
    @ValueSource(strings = {"일부구역 동반가능", "일부 구역 동반가능", "전구역동반가능", "실외만 동반가능"})
    void 표기가_다르면_전_구역으로_보지_않는다(String area) {
        // 원문 표기가 정확히 일치할 때만 참이다. 느슨하게 맞추면 "일부구역" 이 "구역" 을 포함한다는
        // 이유로 통과할 수 있는데, 그 실수의 대가는 사용자가 가서 못 들어가는 것이다.
        assertFalse(place().accompanyArea(area).build().allowsWholeArea());
    }

    @ParameterizedTest
    @NullSource
    void 동반_구역을_모르면_전_구역이라고_답하지_않는다(String area) {
        // 모르는 것을 "전 구역 가능" 으로 내리면 사용자가 가서야 알게 된다.
        assertFalse(place().accompanyArea(area).build().allowsWholeArea());
    }

    @Test
    void 조건을_하나라도_알면_열_내용이_있다() {
        assertTrue(place().accompanyPet("전 견종 동반 가능").build().knowsCondition());
        assertTrue(place().requiredMatter("목줄 착용").build().knowsCondition());
        assertTrue(place().etcInfo("배변봉투 지참").build().knowsCondition());
        assertTrue(place().accompanyArea("전구역 동반가능").build().knowsCondition());
    }

    @Test
    void 조건을_하나도_모르면_열_내용이_없다() {
        // 상세 조회가 실패한 장소다 — 칩은 뜨고 상세만 빈다. 빈 모달을 띄우면 로딩 실패로 보인다.
        assertFalse(place().build().knowsCondition());
    }

    @Test
    void 시설을_하나라도_알면_그_칸을_그린다() {
        assertTrue(place().facilities("반려동물 놀이터").build().knowsFacilities());
        assertTrue(place().providedItems("배변봉투").build().knowsFacilities());
        assertTrue(place().rentalItems("유모차").build().knowsFacilities());
    }

    @Test
    void 시설을_모르면_그_칸을_접는다() {
        // 실측 15건 중 14건이 이쪽이다. 빈 칸을 남기면 "정보가 있는데 못 받아왔다" 처럼 보인다.
        assertFalse(place().build().knowsFacilities());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 선택값이_비어_있으면_null_로_접는다(String blank) {
        PetFriendlyPlace built = place().accompanyPet(blank).etcInfo(blank).facilities(blank).build();

        assertNull(built.getAccompanyPet());
        assertNull(built.getEtcInfo());
        assertNull(built.getFacilities());
        assertFalse(built.knowsCondition(), "빈 문자열을 값으로 세면 열 내용이 없는데 있다고 답한다");
    }

    @Test
    void 선택값이_길면_버리지_않고_잘라_담는다() {
        // 유의사항 한 줄이 길다고 장소를 통째로 버리면 "데려갈 수 있다" 는 사실까지 잃는다.
        String tooLong = "가".repeat(600);

        PetFriendlyPlace built = place().requiredMatter(tooLong).build();

        assertEquals(500, built.getRequiredMatter().length());
    }

    @Test
    void 콘텐츠_식별자가_없으면_만들_수_없다() {
        // 이 값이 곧 장소 풀 매칭 키다 — 없으면 어느 장소에 칩을 붙일지 알 수 없다.
        assertThrows(IllegalArgumentException.class, () -> place().contentId(null).build());
        assertThrows(IllegalArgumentException.class, () -> place().contentId("  ").build());
    }

    @Test
    void 지역과_이름과_수집시각은_반드시_있다() {
        assertThrows(NullPointerException.class, () -> place().regionId(null).build());
        assertThrows(IllegalArgumentException.class, () -> place().name(null).build());
        assertThrows(NullPointerException.class, () -> place().fetchedAt(null).build());
    }
}

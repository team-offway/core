package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 장소 보조정보 값객체(#157·#305).
 *
 * <p>여기서 보는 것은 {@link PoiIntro#isEmpty()} 하나다. 이 판정이 <b>"재시도 대기로 둘 것인가"</b> 를
 * 정한다 — 참이면 배치가 빈 행으로 남기고 일정 시간 뒤 다시 묻는다. 잘못 판정하면 값이 있는데 계속
 * 다시 묻거나(예산 낭비), 영영 안 물어 화면이 빈 채로 굳는다.
 */
class PoiIntroTest {

    @Test
    void 아무_값도_없으면_비어_있다() {
        assertTrue(PoiIntro.builder().build().isEmpty());
    }

    /**
     * <b>어느 칸이든 하나만 차면 비어 있지 않다.</b>
     *
     * <p>카테고리마다 채워지는 칸이 다르다 — 음식점은 대표메뉴가, 숙박은 객실 수가 온다. 운영시간만
     * 보고 판정하면 대표메뉴만 온 음식점이 "빈 응답" 으로 취급돼 매 회차 다시 불린다.
     */
    @Test
    void 대표메뉴만_있어도_비어_있지_않다() {
        assertFalse(PoiIntro.builder().signatureMenu("갈치조림정식").build().isEmpty());
    }

    @Test
    void 객실수만_있어도_비어_있지_않다() {
        assertFalse(PoiIntro.builder().roomCount("13실").build().isEmpty());
    }

    /** 체험은 이 칸만 오는 경우가 흔하다 — 운영시간만 보면 빈 응답으로 오판한다. */
    @Test
    void 체험안내만_있어도_비어_있지_않다() {
        assertFalse(PoiIntro.builder().experienceGuide("목공예 체험 / 도자기 체험").build().isEmpty());
    }

    @Test
    void 운영시간만_있어도_비어_있지_않다() {
        assertFalse(PoiIntro.builder().useTime("09:00~18:00").build().isEmpty());
    }

    /**
     * 공백만 있는 값은 없는 것으로 본다.
     *
     * <p>외부가 빈 문자열·공백을 실어 보낸다. 그것을 값으로 치면 영원히 재시도 대상에서 빠져,
     * 원본이 나중에 채워도 우리는 영영 모른다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void 공백만_있는_값은_비어_있는_것으로_본다(String blank) {
        assertTrue(PoiIntro.builder().signatureMenu(blank).useTime(blank).build().isEmpty());
    }
}

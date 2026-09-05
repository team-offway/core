package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 연동을 어떻게 굴릴지(#403).
 *
 * <p>여기서 지키는 것 하나 — <b>기본값이 지금 동작이어야 한다.</b> 이 기능이 붙었다는 이유만으로
 * 아무것도 안 건드린 연동의 동작이 달라지면 안 된다.
 */
class ExternalApiSettingTest {

    @Test
    void 손대지_않은_연동은_캐시를_쓰고_배치_상한이_없다() {
        ExternalApiSetting setting = ExternalApiSetting.defaultFor(ExternalApi.TOUR_API);

        assertTrue(setting.cacheEnabled());
        assertEquals(null, setting.batchLimit());
        assertTrue(setting.isDefault());
    }

    @Test
    void 상한이_없으면_배치가_얼마를_썼든_돈다() {
        ExternalApiSetting setting = ExternalApiSetting.defaultFor(ExternalApi.TOUR_API);

        assertTrue(setting.allowsBatch(999_999));
    }

    /**
     * 상한은 <b>그 API 의 오늘 총 사용량</b>과 견준다.
     *
     * <p>배치만 따로 세지 않는 것은, 막으려는 것이 "배치가 사용자 몫을 먹는 것" 이기 때문이다 —
     * 사용자가 이미 많이 썼으면 배치는 더 일찍 물러나야 한다.
     */
    @Test
    void 상한에_닿으면_배치가_물러난다() {
        ExternalApiSetting setting = new ExternalApiSetting(ExternalApi.TOUR_API, true, 700);

        assertTrue(setting.allowsBatch(699));
        assertFalse(setting.allowsBatch(700));
        assertFalse(setting.allowsBatch(701));
    }

    /** 0 은 "배치를 아예 안 돌린다" 라 정당한 값이다. */
    @Test
    void 상한_0_은_배치를_통째로_멈춘다() {
        ExternalApiSetting setting = new ExternalApiSetting(ExternalApi.TOUR_API, true, 0);

        assertFalse(setting.allowsBatch(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -100})
    void 음수_상한은_받지_않는다(int limit) {
        assertThrows(ExternalApiSettingException.class,
                () -> new ExternalApiSetting(ExternalApi.TOUR_API, true, limit));
    }

    /**
     * 일일 한도보다 큰 상한은 <b>무제한과 같은데 화면에는 제한이 걸린 것처럼 보인다.</b>
     *
     * <p>조용히 뜻이 다른 값을 받지 않는다 — 어드민이 "상한 5000 을 걸었다" 고 믿는 동안 배치는
     * 한도를 그대로 태운다.
     */
    @Test
    void 일일_한도보다_큰_상한은_받지_않는다() {
        assertThrows(ExternalApiSettingException.class,
                () -> new ExternalApiSetting(ExternalApi.TOUR_API, true, ExternalApi.TOUR_API.dailyLimit() + 1));
    }

    @Test
    void 일일_한도와_같은_상한은_받는다() {
        ExternalApiSetting setting =
                new ExternalApiSetting(ExternalApi.TOUR_API, true, ExternalApi.TOUR_API.dailyLimit());

        assertFalse(setting.isDefault());
    }

    @Test
    void 캐시를_끄면_기본값이_아니다() {
        assertFalse(new ExternalApiSetting(ExternalApi.TOUR_API, false, null).isDefault());
    }
}

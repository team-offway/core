package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * <b>서비스마다 한도를 따로 센다</b>(#496).
 *
 * <h2>왜 이것을 잠그나</h2>
 *
 * <p>관광빅데이터의 세 서비스(방문자수·중심관광지·연관관광지)를 {@code TOUR_DATA_LAB} 한 칸에 합쳐
 * 세고 있었다. 인증키가 하나라 같은 한도인 줄 알았는데, 공공데이터포털은 <b>활용신청 단위</b>로 한도를
 * 주고 셋은 각자 별개 신청이다(15101972 · 15128559 · 15128560, 각 1,000).
 *
 * <p>합쳐 세면 <b>남의 소비가 내 한도를 먹는다.</b> 2026-09-07 에 연관관광지가 806, 중심관광지가 194 를
 *써서 합이 1,000 이 되자 소진 알림이 울렸고 배치가 스스로 멈췄다 — 정작 <b>방문자수는 한 콜도 안
 * 썼다.</b> 그리고 그때 실제로 호출해 보면 셋 다 정상 응답이었다.
 *
 * <p>이건 <b>알림만의 문제가 아니다.</b> 같은 집계를 {@code batchMayCall} 이 보고 배치를 멈춘다.
 *
 * <p>깨져도 화면이 멀쩡하고 예외도 안 나는 종류라 테스트로만 잡힌다.
 */
class ExternalApiQuotaSeparationTest {

    /**
     * 관광빅데이터 세 서비스는 <b>서로 다른 칸</b>이어야 한다.
     *
     * <p>다시 하나로 합치면 이 단언이 깨진다.
     */
    @Test
    void 관광빅데이터_세_서비스는_각자_한도를_갖는다() {
        Set<ExternalApi> datalab = Set.of(
                ExternalApi.TOUR_VISITOR,
                ExternalApi.TOUR_HUB_ATTRACTION,
                ExternalApi.TOUR_RELATED_ATTRACTION);

        assertEquals(3, datalab.size(), "셋이 같은 상수로 접히면 한도를 나눠 쓰게 된다");
        for (ExternalApi api : datalab) {
            assertEquals(1_000, api.dailyLimit(), api.label() + " 는 포털 확인값이 1,000 이다");
        }
    }

    /**
     * <b>한 서비스를 다 써도 다른 서비스는 멀쩡하다.</b>
     *
     * <p>합쳐 세던 시절의 증상을 그대로 재현한다 — 연관관광지가 자기 한도를 다 태운 상황에서
     * 방문자수가 소진으로 판정되면 안 된다.
     */
    @Test
    void 한_서비스를_다_써도_다른_서비스는_남아_있다() {
        long burned = ExternalApi.TOUR_RELATED_ATTRACTION.dailyLimit();

        assertEquals(0, ExternalApi.TOUR_RELATED_ATTRACTION.remainingAfter(burned),
                "자기 한도를 다 쓰면 0 이어야 한다");
        assertEquals(ExternalApi.TOUR_VISITOR.dailyLimit(),
                ExternalApi.TOUR_VISITOR.remainingAfter(0),
                "남의 소비는 내 잔량을 건드리지 않는다");
        assertTrue(ExternalApi.TOUR_HUB_ATTRACTION.remainingAfter(194) > 0,
                "중심관광지는 194 를 써도 한참 남는다");
    }

    /**
     * <b>사람이 읽는 이름이 서비스마다 달라야 한다.</b>
     *
     * <p>알림·어드민 표에 이 이름이 그대로 나간다. 셋이 같은 이름이면 "관광빅데이터 1000/1000" 처럼
     * 어느 서비스가 마른 건지 알 수 없는 알림으로 되돌아간다 — 그게 이 이슈를 만든 화면이다.
     */
    @Test
    void 라벨이_서비스를_구분한다() {
        assertNotEquals(ExternalApi.TOUR_VISITOR.label(), ExternalApi.TOUR_HUB_ATTRACTION.label());
        assertNotEquals(ExternalApi.TOUR_HUB_ATTRACTION.label(), ExternalApi.TOUR_RELATED_ATTRACTION.label());
        assertNotEquals(ExternalApi.TOUR_VISITOR.label(), ExternalApi.TOUR_RELATED_ATTRACTION.label());
    }

    /**
     * <b>모든 API 의 라벨은 서로 달라야 한다.</b>
     *
     * <p>위 셋만 보면 다음에 다른 계열이 같은 실수를 할 때 안 걸린다. 라벨이 겹치는 순간 알림·어드민
     * 표에서 두 서비스가 한 줄로 보이고, 그러면 합쳐 세는 것과 증상이 같아진다.
     */
    @Test
    void 모든_API_라벨이_유일하다() {
        Set<String> labels = new HashSet<>();
        for (ExternalApi api : ExternalApi.values()) {
            assertTrue(labels.add(api.label()), "라벨이 겹친다: " + api.label());
        }
    }
}

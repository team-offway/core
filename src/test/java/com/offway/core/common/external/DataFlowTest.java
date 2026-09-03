package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 데이터 출처 지도(#398).
 *
 * <p>이건 <b>사람이 적는 표</b>라 코드가 스스로 지켜 주지 않는다. 그래서 여기서 잠근다 —
 * 빈 설명, 없는 화면, 뒤바뀐 정렬처럼 <b>표를 못 믿게 만드는 것</b>을 막는 것이 목적이다.
 */
class DataFlowTest {

    @ParameterizedTest
    @EnumSource(DataFlow.class)
    void 모든_흐름은_화면과_방식과_설명을_갖는다(DataFlow flow) {
        assertNotNull(flow.screen(), flow + " 의 화면이 비었다");
        assertNotNull(flow.mode(), flow + " 의 방식이 비었다");
        assertFalse(flow.note().isBlank(), flow + " 의 설명이 비었다");
    }

    /**
     * <b>API 가 null 인 흐름은 {@link DataFlow.Mode#STORED} 뿐이다.</b>
     *
     * <p>"외부를 부르지 않는다" 도 답이라 null 을 허용하는데, 실호출이나 캐시인데 API 가 없으면
     * 그건 적다 만 것이다.
     */
    @ParameterizedTest
    @EnumSource(DataFlow.class)
    void 외부를_안_쓰는_흐름은_DB_로만_적는다(DataFlow flow) {
        if (flow.api() == null) {
            assertEquals(DataFlow.Mode.STORED, flow.mode(), flow + " 는 API 가 없는데 DB 가 아니다");
        }
    }

    @Test
    void 한_API_를_쓰는_흐름을_모아_준다() {
        List<DataFlow> flows = DataFlow.using(ExternalApi.TOUR_API);

        assertFalse(flows.isEmpty());
        assertTrue(flows.stream().allMatch(flow -> flow.api() == ExternalApi.TOUR_API));
    }

    /**
     * <b>실호출이 먼저 온다.</b> 한도를 태우는 쪽이 위에 와야 읽힌다 — 캐시·DB 는 그날 호출량에
     * 거의 기여하지 않는데 목록 위를 차지하면 위험한 것이 아래로 밀린다.
     */
    @Test
    void 실호출을_먼저_보여준다() {
        List<DataFlow> flows = DataFlow.using(ExternalApi.TOUR_API);

        assertEquals(DataFlow.Mode.LIVE, flows.get(0).mode());
        assertTrue(isSortedByMode(flows));
    }

    /**
     * 전체 목록은 <b>화면 순서가 먼저</b>다 — {@link DataFlow#using} 과 규칙이 다르다.
     *
     * <p>한 API 를 볼 때는 한도를 태우는 방식이 위에 와야 읽히지만, 전체 표는 화면이 묶여 있지
     * 않으면 같은 화면의 줄이 표 곳곳에 흩어져 "이 화면이 무엇을 부르나" 를 못 읽는다.
     *
     * <p>개수만 세면 정렬이 뒤집혀도 초록이라, 실제 순서를 본다.
     */
    @Test
    void 전체_목록은_화면_안에서_방식_순으로_정렬한다() {
        List<DataFlow> flows = DataFlow.all();

        assertEquals(DataFlow.values().length, flows.size());
        for (int i = 1; i < flows.size(); i++) {
            DataFlow previous = flows.get(i - 1);
            DataFlow current = flows.get(i);
            int byScreen = previous.screen().compareTo(current.screen());
            assertTrue(byScreen < 0 || (byScreen == 0 && previous.mode().compareTo(current.mode()) <= 0),
                    previous + " 다음에 " + current + " 가 왔다");
        }
    }

    /**
     * 코스 생성이 <b>가장 많이 태우는 화면</b>이라는 사실을 표가 잃지 않게 한다.
     *
     * <p>실측으로 확인한 값이다 — 관광타입 4종을 캐시 없이 부르고, TMAP 도 둘을 실호출한다.
     * 이 셋 중 하나라도 표에서 빠지면 "코스 하나에 몇 콜인가" 를 답할 수 없다.
     */
    @Test
    void 코스_생성의_실호출을_빠짐없이_적는다() {
        List<ExternalApi> live = Arrays.stream(DataFlow.values())
                .filter(flow -> flow.screen() == DataFlow.Screen.COURSE_GENERATE)
                .filter(flow -> flow.mode() == DataFlow.Mode.LIVE)
                .map(DataFlow::api)
                .toList();

        assertTrue(live.contains(ExternalApi.TOUR_API), "슬롯 후보 조회가 빠졌다");
        assertTrue(live.contains(ExternalApi.TMAP_ROUTE), "경로 시간이 빠졌다");
        assertTrue(live.contains(ExternalApi.TMAP_WAYPOINT), "동선 최적화가 빠졌다");
    }

    private static boolean isSortedByMode(List<DataFlow> flows) {
        for (int i = 1; i < flows.size(); i++) {
            if (flows.get(i - 1).mode().compareTo(flows.get(i).mode()) > 0) {
                return false;
            }
        }
        return true;
    }
}

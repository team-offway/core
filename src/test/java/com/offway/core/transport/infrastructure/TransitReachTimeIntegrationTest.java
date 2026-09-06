package com.offway.core.transport.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.TravelTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 도달시간이 <b>역·터미널·항구를 거쳐</b> 나오는가(#58).
 *
 * <h2>왜 통합인가</h2>
 *
 * <p>거점 해석기(역 343·터미널 789·항구 500)가 <b>내부 컴포넌트</b>라 stub 하지 않는다(테스트 규약).
 * 그리고 이 계산의 값어치가 정확히 그 시드에서 나오므로, 가짜 거점으로 재면 아무것도 검증하지 못한다.
 *
 * <h2>무엇을 잠그나</h2>
 *
 * <p>절대값이 아니라 <b>방향</b>이다. 육로가 뚫린 곳은 예전보다 빨라지고 섬은 느려져야 한다 — 그게
 * "거점을 본다" 는 말의 뜻이고, 숫자 하나를 박으면 속도 상수를 조정할 때마다 깨진다.
 */
@SpringBootTest
class TransitReachTimeIntegrationTest {

    /** 서울역 — #443 전수 실측이 쓴 출발지와 같다. */
    private static final Coordinate SEOUL = new Coordinate(37.5547, 126.9707);

    /** 정선군 — 청량리에서 열차가 닿는다. */
    private static final Coordinate JEONGSEON = new Coordinate(37.3805, 128.6608);

    /** 태백시 — 서울경부에서 고속버스 170분(#443 실측). */
    private static final Coordinate TAEBAEK = new Coordinate(37.1641, 128.9856);

    /** 울릉군 — <b>배 말고 닿는 수단이 없다.</b> 직선으로 재면 그 사실이 사라진다. */
    private static final Coordinate ULLEUNG = new Coordinate(37.4844, 130.9053);

    /**
     * 서울에서 40㎞ 이내 지점 — 거점을 거치는 것이 오히려 손해인 거리다.
     *
     * <p>우리 89곳 중에는 이만큼 가까운 곳이 없다(가장 가까운 축인 연천도 60㎞ 다). 그래도 이 경계를
     * 잠그는 이유는 출발지가 지방일 때 <b>가까운 인구감소지역</b>이 생기기 때문이다 — 대구에서 군위,
     * 광주에서 담양이 그렇다.
     */
    private static final Coordinate NEARBY = new Coordinate(37.7547, 127.0707);

    @Autowired
    private TravelTimeProvider travelTimeProvider;

    @Autowired
    private HaversineTravelTimeProvider straightLine;

    /** {@code @Primary} 가 걸려 소비 도메인이 새 어댑터를 받는다 — 이게 안 되면 나머지가 무의미하다. */
    @Test
    void 도달시간_port_가_거점_경유_어댑터로_주입된다() {
        assertTrue(travelTimeProvider instanceof TransitReachTimeProvider,
                "실제 주입된 것: " + travelTimeProvider.getClass().getSimpleName());
    }

    /**
     * <b>자차는 그대로다.</b> 거점을 거치지 않으므로 예전 값과 같아야 한다 — 이 작업이 자차 코스를
     * 건드리면 범위를 벗어난 것이다.
     */
    @Test
    void 자차는_예전_계산_그대로다() {
        assertEquals(
                straightLine.reachMinutes(SEOUL, JEONGSEON, TransportMode.CAR),
                travelTimeProvider.reachMinutes(SEOUL, JEONGSEON, TransportMode.CAR));
    }

    /**
     * <b>가까운 곳도 그대로다.</b> 역까지 갔다가 다시 나오는 것보다 직접 가는 편이 빠른 거리에서는
     * 거점 경유가 실제보다 큰 값을 낸다.
     */
    @Test
    void 가까운_지역은_예전_계산_그대로다() {
        assertTrue(SEOUL.haversineKmTo(NEARBY) < 40, "이 테스트는 40㎞ 미만 전제다");

        assertEquals(
                straightLine.reachMinutes(SEOUL, NEARBY, TransportMode.TRANSIT),
                travelTimeProvider.reachMinutes(SEOUL, NEARBY, TransportMode.TRANSIT));
    }

    /**
     * <b>열차가 닿는 곳은 빨라진다.</b> 직선 × 50㎞/h 로 재던 것을 90㎞/h 간선으로 바꾸면 그렇다.
     *
     * <p>정선은 #443 에서 열차 코스로 잡힌 지역이다.
     */
    @Test
    void 열차가_닿는_지역은_예전보다_빨라진다() {
        int before = straightLine.reachMinutes(SEOUL, JEONGSEON, TransportMode.TRANSIT);
        int after = travelTimeProvider.reachMinutes(SEOUL, JEONGSEON, TransportMode.TRANSIT);

        assertTrue(after < before, "거점 경유 " + after + "분이 직선 " + before + "분보다 빨라야 한다");
    }

    @Test
    void 고속버스가_닿는_지역도_예전보다_빨라진다() {
        int before = straightLine.reachMinutes(SEOUL, TAEBAEK, TransportMode.TRANSIT);
        int after = travelTimeProvider.reachMinutes(SEOUL, TAEBAEK, TransportMode.TRANSIT);

        assertTrue(after < before, "거점 경유 " + after + "분이 직선 " + before + "분보다 빨라야 한다");
    }

    /**
     * <b>없는 항로를 지어내지 않는다.</b>
     *
     * <p>울릉은 배 말고 닿는 수단이 없는데, 서울에는 항구가 없다(#443 이 이미 기록했다 — 여객선 3곳이
     * "출발 지점 없음"). 그러면 출발지 최근접 항구는 인천 어딘가가 잡히고, <b>인천에서 울릉으로 가는
     * 배는 없다.</b>
     *
     * <p>그 구간을 평균속도로 추정하면 "배로 가면 되겠네" 라는 틀린 답이 나온다. 실측이 없으면 여객선을
     * 후보에서 빼고, 그 결과 직선 추정으로 되돌아간다 — <b>지금과 같은 값이지만 틀린 답은 아니다.</b>
     *
     * <p>실측이 쌓이면(그 항로를 실제로 재 보면) 그때 정확해진다.
     */
    @Test
    void 실측이_없는_항로는_지어내지_않는다() {
        int before = straightLine.reachMinutes(SEOUL, ULLEUNG, TransportMode.TRANSIT);
        int after = travelTimeProvider.reachMinutes(SEOUL, ULLEUNG, TransportMode.TRANSIT);

        assertEquals(before, after,
                "실측 없는 항로를 추정하면 갈 수 없는 곳이 후보에 오른다");
    }

    /** 같은 입력이면 같은 답이다 — 추천이 매번 다른 순서를 내면 사용자가 목록을 못 믿는다. */
    @Test
    void 같은_입력은_같은_값을_낸다() {
        int first = travelTimeProvider.reachMinutes(SEOUL, JEONGSEON, TransportMode.TRANSIT);
        int second = travelTimeProvider.reachMinutes(SEOUL, JEONGSEON, TransportMode.TRANSIT);

        assertEquals(first, second);
    }

    /** 도달시간은 언제나 양수다 — 0이나 음수가 나오면 도달 필터가 통째로 무너진다. */
    @Test
    void 도달시간은_양수다() {
        assertTrue(travelTimeProvider.reachMinutes(SEOUL, ULLEUNG, TransportMode.TRANSIT) > 0);
        assertTrue(travelTimeProvider.reachMinutes(SEOUL, JEONGSEON, TransportMode.TRANSIT) > 0);
    }
}

package com.offway.core.transport.domain;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 고속·시외가 <b>같은 곳에 내리는가</b>, 그렇다면 어느 쪽을 대표로 삼는가(#551).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>도착 지점을 지금까지 <b>종류를 안 가린 최근접</b>으로 골랐다. 그런데 군 단위에는 종합터미널이 흔해
 * 고속·시외가 <b>같은 자리</b>에 있고, 그때 어느 쪽이 뽑히는지는 사실상 우연이다.
 *
 * <pre>
 *   양양  대표: INTERCITY_BUS → 양양터미널   소요시간 없음
 *         대안: EXPRESS_BUS   → 양양터미널   소요시간 있음   ← 같은 곳인데 시각을 안다
 * </pre>
 *
 * <p>내리는 곳이 같으므로 <b>바꿔도 동선이 안 바뀐다.</b> 얻는 것은 도착 시각뿐이고, 그 값이 코스의
 * 첫날 일정을 자르는 근거다(#127) — 모르면 "하루 전부" 로 가정해 서울에서 세 시간 걸리는 지역에 오전
 * 일정이 들어간다.
 *
 * <h2>같은 곳의 기준</h2>
 *
 * <p>좌표가 <b>완전히 같지는 않다</b> — 같은 종합터미널이라도 고속·시외 코드가 따로 있고 좌표가 미세하게
 * 갈린다. {@value #SAME_POINT_KM}km 는 그 흔들림을 덮으면서 <b>다른 터미널을 같다고 보지 않는 값</b>이다.
 * 실측(2026-09-14, 89곳)에서 두 종류의 최근접이 이 안에 드는 지역이 52곳이고, 그 바깥은 최소 1km 넘게
 * 떨어져 있어 경계가 뚜렷하다.
 *
 * <p><b>지점이 다르면 손대지 않는다.</b> 그때는 동선이 실제로 바뀌고, 다른 지역으로 넘어가는 사례까지
 * 있어(연천역→철원터미널) #542 가 세운 "그 지역에 닿는가" 규칙과 얽힌다. 그 축은 별도 판단이다.
 */
public final class SameArrivalPoint {

    /**
     * 같은 곳으로 보는 거리.
     *
     * <p>종합터미널의 고속·시외 좌표 차이를 덮는 값이다. 더 키우면 길 건너 다른 터미널이 묶이고,
     * 더 줄이면 같은 건물이 갈린다.
     */
    private static final double SAME_POINT_KM = 0.3;

    private SameArrivalPoint() {
    }

    /**
     * 둘이 같은 곳에 내리는가.
     *
     * <p>한쪽이라도 없으면 견줄 것이 없으므로 거짓이다 — 그때는 있는 쪽이 그대로 대표가 된다.
     */
    public static boolean is(Terminal one, Terminal other) {
        if (one == null || other == null) {
            return false;
        }
        return one.coordinate().haversineKmTo(other.coordinate()) <= SAME_POINT_KM;
    }

    /**
     * 같은 곳에 내리는 둘 중 <b>도착 시각을 아는 쪽</b>을 고른다.
     *
     * <p>규칙은 셋이고 순서가 곧 규칙이다.
     *
     * <ol>
     *   <li>같은 곳이 아니면 그대로 둔다 — 동선이 바뀌는 선택은 이 함수가 하지 않는다
     *   <li>둘 다 알거나 둘 다 모르면 그대로 둔다 — 바꿔도 얻는 것이 없다
     *   <li>한쪽만 알면 <b>아는 쪽</b>을 고른다
     * </ol>
     *
     * @param current 지금까지의 대표(종류를 안 가린 최근접)
     * @param express 그 지역의 고속 터미널
     * @param intercity 그 지역의 시외 터미널
     * @param knowsArrivalTime 그 터미널로 가는 도착 시각을 아는가 — 조회는 호출자가 소유한다
     */
    public static Optional<Terminal> timeAware(
            Optional<Terminal> current, Optional<Terminal> express, Optional<Terminal> intercity,
            Predicate<Terminal> knowsArrivalTime) {
        if (current.isEmpty() || express.isEmpty() || intercity.isEmpty()) {
            return current;
        }
        Terminal e = express.get();
        Terminal i = intercity.get();
        if (!is(e, i)) {
            return current;
        }
        boolean knowsExpress = knowsArrivalTime.test(e);
        boolean knowsIntercity = knowsArrivalTime.test(i);
        if (knowsExpress == knowsIntercity) {
            return current;
        }
        return Optional.of(knowsExpress ? e : i);
    }
}

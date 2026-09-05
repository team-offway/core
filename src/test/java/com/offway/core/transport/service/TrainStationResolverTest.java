package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Station;
import com.offway.core.transport.domain.TrainStation;
import com.offway.core.transport.repository.TrainStationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 좌표를 역으로 해석하는 규칙 — 특히 <b>대안 역을 어디까지 후보로 볼 것인가</b>(#435).
 *
 * <p><b>출발지는 전국 어디든 될 수 있다.</b> 수도권만 보면 3순위 역까지 4㎞ 안이라 아무 문제가 없어 보이는데,
 * 강릉에서는 강릉역 다음이 26㎞ 밖이다. 같은 규칙이 곳에 따라 전혀 다른 답을 내므로 양쪽을 함께 잠근다.
 *
 * <p>좌표는 전부 시드 실측값이다. 이 규칙은 <b>거리 몇 ㎞ 차이</b>가 전부라, 지어낸 좌표로는 검증이 되지 않는다.
 */
class TrainStationResolverTest {

    /** 서울 도심 — 3순위까지 4.2㎞ 안에 모인다. */
    private static final double SEOUL_LAT = 37.5547;
    private static final double SEOUL_LNG = 126.9707;

    /** 강릉 교동 — 강릉역 2.2㎞ 다음이 정동진 15.8㎞, 망상해변 26.7㎞ 다. */
    private static final double GANGNEUNG_LAT = 37.7640;
    private static final double GANGNEUNG_LNG = 128.8760;

    /** 제주 — 철도가 없다. 가장 가까운 역이 바다 건너 126㎞ 밖이다. */
    private static final double JEJU_LAT = 33.4996;
    private static final double JEJU_LNG = 126.5312;

    private static final List<TrainStation> MASTER = List.of(
            TrainStation.of("NAT010000", "서울", 37.553261, 126.969133),
            TrainStation.of("NAT010032", "용산", 37.528736, 126.964190),
            TrainStation.of("NAT130036", "서빙고", 37.519988, 126.989521),
            TrainStation.of("NAT130070", "옥수", 37.541458, 127.017157),
            TrainStation.of("NAT600000", "강릉", 37.763871, 128.898892),
            TrainStation.of("NAT601861", "정동진", 37.690278, 129.033611),
            TrainStation.of("NAT601500", "망상해변", 37.560000, 129.098000),
            TrainStation.of("NAT470312", "해남", 34.636390, 126.638330));

    private final TrainStationResolver resolver = new TrainStationResolver(() -> MASTER);

    @Test
    void 가까운_순으로_요청한_수만큼_준다() {
        List<Station> candidates = resolver.nearestCandidates(SEOUL_LAT, SEOUL_LNG, 3);

        assertEquals(List.of("서울", "용산", "서빙고"), candidates.stream().map(Station::name).toList());
    }

    /**
     * 시골에서는 후보가 하나뿐인 것이 정답이다.
     *
     * <p>반경(50㎞)만 보면 정동진·망상해변이 통과한다. 그러면 "열차를 타려면 26㎞ 떨어진 망상해변으로
     * 가세요" 가 되는데, 그건 안내가 아니라 다른 여행이다. 최근접 역에서 10㎞ 안쪽만 대안으로 본다.
     */
    @Test
    void 다음_역이_멀면_후보로_넣지_않는다() {
        List<Station> candidates = resolver.nearestCandidates(GANGNEUNG_LAT, GANGNEUNG_LNG, 3);

        assertEquals(List.of("강릉"), candidates.stream().map(Station::name).toList());
    }

    /** 반경 밖이면 빈 목록 — "열차로 못 감" 이다. 제주에는 철도가 없다. */
    @Test
    void 반경_안에_역이_없으면_비어_있다() {
        assertTrue(resolver.nearestCandidates(JEJU_LAT, JEJU_LNG, 3).isEmpty());
    }

    /** {@code nearest} 는 후보의 첫 곳과 같아야 한다 — 두 경로가 갈리면 화면과 조회가 어긋난다. */
    @Test
    void 최근접_하나는_후보의_첫_곳과_같다() {
        assertEquals(
                resolver.nearestCandidates(SEOUL_LAT, SEOUL_LNG, 3).getFirst(),
                resolver.nearest(SEOUL_LAT, SEOUL_LNG).orElseThrow());
    }
}

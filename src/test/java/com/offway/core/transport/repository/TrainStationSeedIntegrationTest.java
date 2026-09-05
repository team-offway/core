package com.offway.core.transport.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TrainStation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 역 시드 좌표 검증(#427) — <b>이름만으로 붙인 좌표가 엉뚱한 역을 가리키는지</b> 잡는다.
 *
 * <p><b>왜 필요한가.</b> 시드는 역명으로 좌표를 붙였는데, 같은 이름의 도시철도역이 있으면 그쪽으로 붙는다.
 * 실제로 경전선 원북역(경남 함안)이 서울 광진구 좌표를 달고 있었다. 역 해석은 좌표 최근접이라
 * ({@code TrainStationResolver}, 반경 50㎞) 광진·강동에서 출발하면 그 역이 최근접으로 잡히고, 서버는
 * "경남 원북 → 제천" 이라는 있지도 않은 구간을 조회한다. 열차가 없다고 나오니 시간표가 통째로 빈다.
 *
 * <p><b>화면에는 아무 이상이 없어 보인다</b> — 코스는 그대로 나가고 대표 수단만 조용히 바뀐다. 그래서
 * 실기기로 "왜 여기서만 안 뜨지" 를 밟기 전에는 발견되지 않는다. 여기서 잡는 이유다.
 */
@SpringBootTest
class TrainStationSeedIntegrationTest {

    /** 시드된 역 수(V20260728231041). 마이그레이션이 잘리면 드러나게 정확한 값으로 고정한다. */
    private static final int EXPECTED_STATIONS = 343;

    /**
     * 그중 좌표를 가진 역 — 341곳.
     *
     * <p>둘({@code 구서경주}·{@code 구안강})은 좌표가 없다. 이설 전 옛 역이라 지오코딩이 붙지 않았고,
     * 좌표가 없으면 역 해석에서 그냥 빠진다. 숫자를 고정해 <b>좌표가 조용히 더 사라지는 것</b>을 잡는다.
     */
    private static final int EXPECTED_WITH_COORDINATE = 341;

    /**
     * 같은 노선의 가장 가까운 역까지 허용 거리.
     *
     * <p><b>실측으로 고른 값이다.</b> 정정 후 일반선의 최대는 36.7㎞(경강선 판교-부발)이고, 오지오코딩된
     * 열 곳은 전부 77㎞ 이상이었다. 그 사이를 가른다 — 아래로 13㎞, 위로 17㎞ 여유가 있다.
     */
    private static final double MAX_NEIGHBOUR_KM = 60.0;

    /**
     * 고속선 코드 접두어. <b>이 노선들은 제외한다</b> — 광명·천안아산·김천구미처럼 역 간격이 원래 70~93㎞라
     * 같은 잣대를 들이대면 정상인 역이 걸린다. 정차역을 건너뛰는 것이 고속선의 성격이다.
     */
    private static final String EXPRESS_LINE_PREFIX = "NATH";

    /** 노선을 가르는 코드 자릿수 — {@code NAT88}(경전선)·{@code NAT60}(영동선) 처럼 앞 5자리가 노선이다. */
    private static final int LINE_CODE_LENGTH = 5;

    @Autowired
    private TrainStationRepository stationRepository;

    @Test
    void 시드된_역과_좌표가_전부_남아_있다() {
        List<TrainStation> stations = stationRepository.findAll();

        assertEquals(EXPECTED_STATIONS, stations.size());
        assertEquals(EXPECTED_WITH_COORDINATE, stations.stream().filter(TrainStation::hasCoordinate).count());
    }

    /**
     * 노선에서 혼자 튀는 역이 없어야 한다.
     *
     * <p>철도역은 노선을 따라 이어져 있어, 같은 노선에 다른 역이 있는 한 <b>어느 하나는 가까이 있다.</b>
     * 그 성질이 깨졌다면 좌표가 그 역의 것이 아니다 — 이름이 같은 다른 역에 붙었다는 뜻이다.
     *
     * <p>실패 메시지에 어느 역이 어디로 얼마나 튀었는지를 담는다. "좌표가 이상하다" 만으로는 위키백과에서
     * 무엇을 찾아 고쳐야 할지 알 수 없다.
     */
    @Test
    void 역_좌표가_같은_노선의_다른_역과_동떨어져_있지_않다() {
        Map<String, List<TrainStation>> byLine = new LinkedHashMap<>();
        for (TrainStation station : stationRepository.findAll()) {
            if (!station.hasCoordinate() || station.getCode().startsWith(EXPRESS_LINE_PREFIX)) {
                continue;
            }
            byLine.computeIfAbsent(lineOf(station), line -> new ArrayList<>()).add(station);
        }

        List<String> outliers = new ArrayList<>();
        byLine.forEach((line, stations) -> {
            // 노선에 역이 하나뿐이면 견줄 상대가 없다 — 통과시킨다. 오검출보다 미검출이 낫다.
            if (stations.size() < 2) {
                return;
            }
            for (TrainStation station : stations) {
                double nearest = stations.stream()
                        .filter(other -> !other.getCode().equals(station.getCode()))
                        .mapToDouble(other -> coordinateOf(station).haversineKmTo(coordinateOf(other)))
                        .min()
                        .orElseThrow();
                if (nearest > MAX_NEIGHBOUR_KM) {
                    outliers.add("%s(%s) 노선 %s — 가장 가까운 동료 역까지 %.0f㎞, 좌표 (%s, %s)"
                            .formatted(station.getName(), station.getCode(), line, nearest,
                                    station.getLat(), station.getLng()));
                }
            }
        });

        assertTrue(outliers.isEmpty(),
                "노선에서 동떨어진 역이 있습니다 — 같은 이름의 다른 역 좌표가 붙었는지 확인하세요:\n"
                        + String.join("\n", outliers));
    }

    private static String lineOf(TrainStation station) {
        return station.getCode().substring(0, LINE_CODE_LENGTH);
    }

    private static Coordinate coordinateOf(TrainStation station) {
        return new Coordinate(station.getLat(), station.getLng());
    }
}

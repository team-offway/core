package com.offway.core.transport.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.TrainStation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    /**
     * 역을 묶는 코드 자릿수 — {@code NAT88}·{@code NAT60} 처럼 앞 5자리다.
     *
     * <p><b>이것은 "노선" 이 아니라 TAGO 가 코드를 나눠 준 묶음이다.</b> 대체로 노선과 겹치지만
     * 정확히 같지는 않다 — 예컨대 {@code NAT88} 은 경전선 역들과 함께 광주선 종착역인 광주역을 담는다.
     * 시드에 노선 필드가 없어({@code code}·{@code name}·{@code lat}·{@code lng} 뿐) 더 정확한 축은 없다.
     *
     * <p><b>그래도 이 묶음으로 충분한 이유</b> — 여기서 보려는 것은 노선의 정확한 소속이 아니라
     * <b>지리적 연속성</b>이다. 이 묶음은 실제로 한 회랑을 이룬다: 정정 후 실측하면 묶음 안 최근접
     * 거리가 최대 36.7㎞고, 이번에 찾은 오류 열 곳은 전부 77㎞ 이상이었다.
     */
    private static final int CODE_GROUP_LENGTH = 5;

    /**
     * 이 값에서 이만큼 벗어나면 다른 지점이라고 본다.
     *
     * <p>좌표를 더 정밀한 값으로 다듬는 것은 막지 않되, <b>다른 역으로 옮겨가는 것</b>은 잡을 만큼
     * 좁게 둔다. 이웃 역까지가 보통 3㎞ 이상이라 1㎞면 그 둘을 가른다.
     */
    private static final double COORDINATE_TOLERANCE_KM = 1.0;

    @Autowired
    private TrainStationRepository stationRepository;

    /**
     * 제자리로 돌린 열 곳(#427) — 코드와 위키백과 확인 좌표.
     *
     * <p>회랑 검사만으로는 부족하다. 좌표가 <b>같은 회랑 안의 다른 역</b> 근처로 옮겨가면 60㎞ 검사를
     * 통과해 버린다 — 원북을 3.6㎞ 떨어진 군북 자리에 두는 식이다. 그래서 값 자체를 못 박는다.
     */
    private record CorrectedStation(String code, String name, double lat, double lng) {}

    private static List<CorrectedStation> correctedStations() {
        return List.of(
                new CorrectedStation("NAT880644", "원북", 35.2335694, 128.3263306),
                new CorrectedStation("NAT880702", "평촌", 35.1863194, 128.3310560),
                new CorrectedStation("NAT883012", "광주", 35.1653170, 126.9091880),
                new CorrectedStation("NAT752319", "기성", 36.7902800, 129.4475000),
                new CorrectedStation("NAT752428", "매화", 36.8827800, 129.4097200),
                new CorrectedStation("NAT750254", "송정", 35.1809278, 129.1999306),
                new CorrectedStation("NAT280090", "가남", 37.1976000, 127.5353000),
                new CorrectedStation("NAT600655", "양원", 36.9647639, 129.0915694),
                new CorrectedStation("NAT601275", "신기", 37.3460306, 129.0853361),
                new CorrectedStation("NAT030396", "연산", 36.2122200, 127.2005600));
    }

    /**
     * 정정한 좌표가 그대로 남아 있어야 한다(#427).
     *
     * <p>이 열 곳은 전부 <b>같은 이름의 도시철도역</b>이 있어, 시드를 다시 만들거나 지오코딩을 다시
     * 돌리면 같은 자리로 돌아가기 쉽다. 회랑 검사가 그중 먼 것은 잡지만 가까운 것은 놓치므로
     * 값으로 잠근다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("correctedStations")
    void 정정한_역은_확인된_좌표에_있다(CorrectedStation corrected) {
        TrainStation station = stationRepository.findAll().stream()
                .filter(each -> each.getCode().equals(corrected.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(corrected.name() + " 역이 시드에 없습니다"));

        double off = new Coordinate(station.getLat(), station.getLng())
                .haversineKmTo(new Coordinate(corrected.lat(), corrected.lng()));

        assertTrue(off <= COORDINATE_TOLERANCE_KM,
                "%s(%s) 좌표가 확인된 위치에서 %.1f㎞ 벗어났습니다 — 현재 (%s, %s)"
                        .formatted(corrected.name(), corrected.code(), off,
                                station.getLat(), station.getLng()));
    }

    @Test
    void 시드된_역과_좌표가_전부_남아_있다() {
        List<TrainStation> stations = stationRepository.findAll();

        assertEquals(EXPECTED_STATIONS, stations.size());
        assertEquals(EXPECTED_WITH_COORDINATE, stations.stream().filter(TrainStation::hasCoordinate).count());
    }

    /**
     * 코드 묶음에서 혼자 튀는 역이 없어야 한다.
     *
     * <p>철도역은 회랑을 따라 이어져 있어, 같은 묶음에 다른 역이 있는 한 <b>어느 하나는 가까이 있다.</b>
     * 그 성질이 깨졌다면 좌표가 그 역의 것이 아니다 — 이름이 같은 다른 역에 붙었다는 뜻이다.
     *
     * <p><b>한계 하나를 적어 둔다.</b> 같은 묶음에서 <b>둘 이상이 동시에</b> 서로 가까운 곳으로 잘못
     * 박히면 서로를 가려 준다. 실제로 그랬다 — 경전선 광주역이 경기 광주에 있었는데, 같은 묶음의
     * 원북·평촌도 수도권에 박혀 있어 24㎞ 이웃으로 통과하고 있었다. 그 둘을 제자리로 돌리자 250㎞로
     * 드러났다. 지금은 열 곳을 모두 고쳤으므로 <b>이후 하나가 새로 어긋나면 반드시 걸린다.</b>
     *
     * <p>실패 메시지에 어느 역이 어디로 얼마나 튀었는지를 담는다. "좌표가 이상하다" 만으로는 위키백과에서
     * 무엇을 찾아 고쳐야 할지 알 수 없다.
     */
    @Test
    void 역_좌표가_같은_코드묶음의_다른_역과_동떨어져_있지_않다() {
        Map<String, List<TrainStation>> byGroup = new LinkedHashMap<>();
        for (TrainStation station : stationRepository.findAll()) {
            if (!station.hasCoordinate() || station.getCode().startsWith(EXPRESS_LINE_PREFIX)) {
                continue;
            }
            byGroup.computeIfAbsent(codeGroupOf(station), group -> new ArrayList<>()).add(station);
        }

        List<String> outliers = new ArrayList<>();
        byGroup.forEach((group, stations) -> {
            // 묶음에 역이 하나뿐이면 견줄 상대가 없다 — 통과시킨다. 오검출보다 미검출이 낫다.
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
                    outliers.add("%s(%s) 코드묶음 %s — 가장 가까운 동료 역까지 %.0f㎞, 좌표 (%s, %s)"
                            .formatted(station.getName(), station.getCode(), group, nearest,
                                    station.getLat(), station.getLng()));
                }
            }
        });

        assertTrue(outliers.isEmpty(),
                "코드묶음에서 동떨어진 역이 있습니다 — 같은 이름의 다른 역 좌표가 붙었는지 확인하세요:\n"
                        + String.join("\n", outliers));
    }

    private static String codeGroupOf(TrainStation station) {
        return station.getCode().substring(0, CODE_GROUP_LENGTH);
    }

    private static Coordinate coordinateOf(TrainStation station) {
        return new Coordinate(station.getLat(), station.getLng());
    }
}

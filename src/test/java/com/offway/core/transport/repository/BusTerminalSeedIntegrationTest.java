package com.offway.core.transport.repository;

import com.offway.core.common.geo.Coordinate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.BusTerminal;
import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.service.BusTerminalResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 버스 터미널 시드 검증(#107·#97) — 고속·시외 마이그레이션이 실제로 적용되고 우리 지역에 쓸 만한지 본다.
 *
 * <p>시드는 한 번 만들면 끝인 데이터처럼 보이지만, <b>커버리지가 줄면 조용히 버스 안내가 사라진다.</b> 숫자를 여기에
 * 고정해 마이그레이션이 잘리거나 좌표가 빠지면 드러나게 한다.
 */
@SpringBootTest
class BusTerminalSeedIntegrationTest {

    /** TAGO 터미널 목록 실측(2026-08-05) — 고속 452곳 + 시외 337곳. */
    private static final int EXPECTED_EXPRESS = 452;
    private static final int EXPECTED_INTERCITY = 337;
    private static final int EXPECTED_TERMINALS = EXPECTED_EXPRESS + EXPECTED_INTERCITY;

    /**
     * 좌표를 가진 행 수 — <b>316</b>(2026-09-05 재지오코딩 후).
     *
     * <p><b>530 에서 줄었다.</b> 시드는 이름만으로 좌표를 붙여 동음이의에 걸린 값이 섞여 있었다. 도시를
     * 붙여 다시 찾고 주소의 시도가 맞는 것만 남기니(#436) 확인되지 않은 217곳이 빠졌다.
     *
     * <p><b>줄어든 것이 손해가 아니다.</b> 인구감소지역 커버리지는 그대로고(아래 {@link #EXPECTED_REACHABLE_REGIONS}),
     * 빠진 것은 대부분 경유 정류소와 다른 터미널의 좌표를 베껴 쓰던 행이다. 틀린 좌표는 resolver 가
     * 엉뚱한 곳을 답하게 하지만 빈 좌표는 최근접 탐색에서 빠질 뿐이다.
     *
     * <p>정확한 값으로 고정한다. 하한만 보면 좌표가 조용히 줄어도 통과한다.
     */
    private static final int EXPECTED_WITH_COORDINATE = 316;

    /** 인구감소지역 수 — 행안부 고시 89곳. */
    private static final int EXPECTED_REGIONS = 89;

    /**
     * 버스로 닿는 인구감소지역 수 — 고속·시외를 합쳐 <b>88곳</b>이다(2026-08-06 실측).
     *
     * <p>못 닿는 곳은 <b>울릉군 하나</b>뿐이고, 섬이라 버스로는 애초에 갈 수 없다 — 여객선이 필요하다(#97).
     *
     * <p>정확한 값으로 고정한다. 하한을 느슨하게 두면 좌표가 여덟 곳 사라져도 통과해, 시드 회귀를 놓친다.
     */
    private static final int EXPECTED_REACHABLE_REGIONS = 88;

    /** 터미널이 이보다 멀면 "그 지역 터미널" 로 보지 않는다 — resolver 상한과 같은 값. */
    private static final double NEAR_KM = 30.0;

    /** 정정한 좌표에서 이만큼 벗어나면 다른 자리로 본다. */
    private static final double CORRECTED_TOLERANCE_KM = 1.0;

    /** 광나루역 정류소 자리 — 여기서 그 정류소는 0㎞, 동서울 터미널은 1.4㎞다. */
    private static final double GWANGNARU_STOP_LAT = 37.5453;
    private static final double GWANGNARU_STOP_LNG = 127.1035;

    /** 광주 충장로 — 도심 한복판이다. 유스퀘어까지 약 3.8㎞. */
    private static final double GWANGJU_LAT = 35.1489;
    private static final double GWANGJU_LNG = 126.9190;

    /** 서울역 — 89곳 전수 실측(#443)이 쓴 출발 좌표 그대로다. */
    private static final double SEOUL_STATION_LAT = 37.5547;
    private static final double SEOUL_STATION_LNG = 126.9707;

    @Autowired
    private BusTerminalRepository terminalRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private BusTerminalResolver resolver;

    @Test
    void 터미널_마스터가_시드된다() {
        List<BusTerminal> all = terminalRepository.findAll();

        assertEquals(EXPECTED_TERMINALS, all.size(), "터미널 수가 다릅니다 — 마이그레이션이 잘렸는지 확인하세요");
        long withCoordinate = all.stream().filter(BusTerminal::hasCoordinate).count();
        assertEquals(EXPECTED_WITH_COORDINATE, withCoordinate,
                "좌표 있는 터미널 수가 다릅니다 — 시드·검증 규칙이 바뀌었는지 확인하세요");
    }

    @Test
    void 고속과_시외가_모두_시드된다() {
        // 코드 공간이 겹치지 않아 한 테이블에 담되, 어느 쪽이 통째로 빠지면 그 종류의 안내가 사라진다.
        List<BusTerminal> all = terminalRepository.findAll();

        assertEquals(EXPECTED_EXPRESS,
                all.stream().filter(t -> t.getKind() == BusTerminalKind.EXPRESS).count(), "고속버스 터미널 수");
        assertEquals(EXPECTED_INTERCITY,
                all.stream().filter(t -> t.getKind() == BusTerminalKind.INTERCITY).count(), "시외버스 터미널 수");
    }

    @Test
    void 터미널_코드는_중복되지_않는다() {
        List<BusTerminal> all = terminalRepository.findAll();

        long distinct = all.stream().map(BusTerminal::getCode).distinct().count();

        assertEquals(all.size(), distinct, "터미널 코드가 중복됩니다 — 구간 조회가 엉뚱한 곳을 가리킵니다");
    }

    @Test
    void 인구감소지역_거의_전부가_버스로_닿는다() {
        // 이 수가 줄면 버스 안내가 조용히 사라진다. 시드·지오코딩 회귀를 여기서 잡는다.
        List<Region> regions = regionRepository.findAll();
        assertEquals(EXPECTED_REGIONS, regions.size(), "인구감소지역 수가 다릅니다");

        long reachable = regions.stream()
                .filter(region -> resolver.nearest(region.getLat(), region.getLng()).isPresent())
                .count();

        assertEquals(EXPECTED_REACHABLE_REGIONS, reachable,
                "버스로 닿는 지역 수가 다릅니다 — 시드·좌표 회귀를 의심하세요");
    }

    @Test
    void 태백은_자기_터미널로_해석된다() {
        // 동음이의·엉뚱한 좌표를 잡으면 여기서 드러난다. 태백시청 좌표에서 태백터미널이 나와야 한다.
        var terminal = resolver.nearest(37.1641, 128.9856).orElseThrow();

        assertEquals("태백", terminal.name());
        assertTrue(terminal.coordinate().haversineKmTo(
                        new com.offway.core.common.geo.Coordinate(37.1641, 128.9856)) < NEAR_KM,
                "해석된 터미널이 너무 멉니다 — 지오코딩이 다른 지역을 잡았을 수 있습니다");
    }

    /**
     * 서울권 세 터미널이 제자리에 있어야 한다(#436).
     *
     * <p><b>왜 값으로 못 박는가.</b> 이 셋은 이름으로 좌표를 붙이다 엉뚱한 자리에 놓였던 곳이고, 시드를
     * 다시 만들면 같은 자리로 돌아가기 쉽다. 특히 김포공항은 서울역에서 <b>0.2㎞</b> 떨어진 자리에
     * 놓여 있어, 서울 출발 시외버스 코스 30곳의 출발 터미널을 전부 끌어당겼다(#443).
     *
     * <p>허용 오차는 1㎞다 — 좌표를 더 정밀하게 다듬는 것은 막지 않되 다른 자리로 옮겨가는 것은 잡는다.
     */
    @ParameterizedTest(name = "{1}")
    @MethodSource("correctedSeoulTerminals")
    void 서울권_터미널이_제자리에_있다(String code, String name, double lat, double lng) {
        Terminal terminal = terminalRepository.findAll().stream()
                .filter(each -> each.getCode().equals(code))
                .findFirst()
                .map(each -> Terminal.builder()
                        .code(each.getCode())
                        .name(each.getName())
                        .kind(each.getKind())
                        .coordinate(new Coordinate(each.getLat(), each.getLng()))
                        .isTerminal(each.isTerminal())
                        .build())
                .orElseThrow(() -> new AssertionError(name + " 터미널이 시드에 없습니다"));

        double off = terminal.coordinate().haversineKmTo(new Coordinate(lat, lng));

        assertTrue(off <= CORRECTED_TOLERANCE_KM,
                "%s(%s) 좌표가 확인된 위치에서 %.1f㎞ 벗어났습니다".formatted(name, code, off));
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> correctedSeoulTerminals() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "NAI0511601", "동서울", 37.53393134, 127.09476219),
                org.junit.jupiter.params.provider.Arguments.of(
                        "NAI0750501", "김포공항", 37.55994673, 126.80279331),
                org.junit.jupiter.params.provider.Arguments.of(
                        "NAI0750503", "김포공항(도심공항)", 37.55994673, 126.80279331),
                org.junit.jupiter.params.provider.Arguments.of(
                        "NAI0671801", "서울남부", 37.48473869, 127.01618622));
    }

    /**
     * 김포공항이 서울역을 끌어당기지 않아야 한다(#436).
     *
     * <p>좌표를 값으로 고정하는 것과 별개로, <b>증상 자체</b>를 남긴다 — 서울역에서 김포공항이 최근접
     * 시외버스 터미널로 뽑히면 그때가 이 버그의 재발이다.
     */
    @Test
    void 서울역의_최근접_시외버스_터미널은_김포공항이_아니다() {
        Terminal nearest = resolver
                .nearest(SEOUL_STATION_LAT, SEOUL_STATION_LNG, BusTerminalKind.INTERCITY)
                .orElseThrow(() -> new AssertionError("서울역 근처에 시외버스 터미널이 없다"));

        // **코드로 본다.** 이름으로 보면 두 코드 중 하나만 막힌다 — NAI0750503 이 다시 어긋나면
        // 뽑히는 이름이 "김포공항(도심공항)" 이라 이름 단언은 그대로 통과한다.
        assertNotEquals("NAI0750501", nearest.code(),
                "김포공항이 서울역 최근접으로 뽑혔다 — 좌표가 다시 어긋났다");
        assertNotEquals("NAI0750503", nearest.code(),
                "김포공항(도심공항)이 서울역 최근접으로 뽑혔다 — 좌표가 다시 어긋났다");
    }

    /**
     * 광주에서 시외버스를 타면 <b>유스퀘어</b>다(#436).
     *
     * <p>서울만 고쳐서는 안 되는 이유가 여기 있다. 유스퀘어는 시외 시드에서 <b>여수</b>(84㎞), 고속
     * 시드에서 <b>경기 광주</b>(253㎞) 좌표를 달고 있었다. 그래서 광주 도심에서 가장 가까운 시외버스
     * 터미널이 <b>성전</b>(실제로는 강진군에 있는데 좌표가 광주 북구에 박혀 있었다)으로 잡혔다.
     *
     * <p>서울의 김포공항과 정확히 같은 구조다 — 터미널 하나의 좌표가 그 도시 전체의 안내를 망가뜨린다.
     */
    @Test
    void 광주_도심에서_가장_가까운_시외버스_터미널은_유스퀘어다() {
        Terminal nearest = resolver
                .nearest(GWANGJU_LAT, GWANGJU_LNG, BusTerminalKind.INTERCITY)
                .orElseThrow(() -> new AssertionError("광주 도심 근처에 시외버스 터미널이 없다"));

        assertEquals("광주(유·스퀘어)", nearest.name());
    }

    /**
     * 정류소 바로 앞에 서 있어도 <b>터미널</b>을 준다(#446).
     *
     * <p>광나루역 정류소 좌표에서 재면 그 정류소가 <b>0㎞</b>이고 동서울은 1.4㎞다. 거리만 보면 정류소가
     * 이긴다 — 그런데 정류소는 지나가며 서는 곳이라 특정 노선만 선다. "광나루역에서 타세요" 는 그 노선이
     * 목적지로 갈 때만 맞는 말이고, 구간 소요시간·출발 시각 조회도 터미널 코드를 전제한다.
     *
     * <p><b>서울역으로 재면 이 규칙이 안 드러난다.</b> #436 으로 DDP 좌표가 비면서 서울역 최근접이
     * 이미 터미널이 됐다 — 규칙을 지워도 통과한다. 정류소가 실제로 이기는 자리에서 재야 한다.
     */
    @Test
    void 정류소가_더_가까워도_터미널을_앞세운다() {
        Terminal nearest = resolver
                .nearest(GWANGNARU_STOP_LAT, GWANGNARU_STOP_LNG, BusTerminalKind.INTERCITY)
                .orElseThrow(() -> new AssertionError("광나루역 근처에 시외버스 터미널이 없다"));

        assertEquals("동서울", nearest.name());
        assertTrue(nearest.isTerminal(), "정류소가 뽑혔다 — 터미널을 앞세우는 규칙이 깨졌다");
    }

    /**
     * 반경 안에 터미널이 없으면 <b>정류소를 그대로 쓴다</b>(#446).
     *
     * <p>정류소를 버리면 인구감소지역 커버리지가 86곳에서 83곳으로 준다. 우선순위만 바꾸는 것이지
     * 목록에서 빼는 것이 아니다 — 그 세 곳에는 정류소가 유일한 접점이다.
     */
    @Test
    void 정류소가_유일한_접점인_지역이_있다() {
        long onlyStop = regionRepository.findAll().stream()
                .filter(region -> resolver.nearest(region.getLat(), region.getLng(), null)
                        .filter(terminal -> !terminal.isTerminal())
                        .isPresent())
                .count();

        // 정류소를 목록에서 빼면 이 지역들이 버스로 못 가는 곳이 된다 — 커버리지 86 → 83.
        // 우선순위만 바꾸는 것이지 빼는 것이 아님을 여기서 지킨다.
        assertTrue(onlyStop > 0,
                "정류소가 최근접인 지역이 하나도 없다 — 정류소가 목록에서 빠졌는지 확인하세요");
    }
}
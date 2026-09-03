package com.offway.core.transport.repository;

import com.offway.core.common.geo.Coordinate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.BusTerminal;
import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.service.BusTerminalResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
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
     * 검증을 통과해 좌표를 남긴 행 수 — 고속 195 + 시외 335 = 530(2026-08-06 실측).
     *
     * <p>이름만으로 지오코딩하면 동음이의·상호명에 걸리므로 <b>근거로 검증한 것만 남긴다</b> — 시외는
     * API 가 주는 소재지와 대조하고, 고속은 소재지가 없어 좌표 충돌로 걸러낸다. 검증 못 한 좌표는 비운다.
     *
     * <p>정확한 값으로 고정한다. 하한만 보면 좌표가 조용히 줄어도 통과한다.
     */
    private static final int EXPECTED_WITH_COORDINATE = 530;

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
}

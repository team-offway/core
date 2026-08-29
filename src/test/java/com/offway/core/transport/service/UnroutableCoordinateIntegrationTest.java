package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.domain.UnroutableReason;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경로 불가 좌표를 기억하고 차단하는 규칙(#335) — DB 를 거쳐 도는지 본다.
 *
 * <p>여기서 잠그는 것은 <b>"짝이 둘 이상일 때만 차단한다"</b> 한 줄이다. 이 규칙이 무너지면 나쁜 좌표
 * 옆에 있었을 뿐인 멀쩡한 장소가 함께 코스에서 사라진다 — 조용히 사라지는 쪽은 언제나 멀쩡한 장소가
 * 더 많다.
 */
@SpringBootTest
@Transactional
class UnroutableCoordinateIntegrationTest {

    /** 운영에서 실제로 걸린 좌표 — 귀목봉(가평, 해발 1,036m). */
    private static final Coordinate GWIMOK = new Coordinate(37.9419179195, 127.3836649009);

    /** 귀목봉의 앞뒤 슬롯이었을 멀쩡한 장소들. */
    private static final Coordinate BEFORE = new Coordinate(37.8300000, 127.5100000);
    private static final Coordinate AFTER = new Coordinate(37.8800000, 127.4200000);

    @Autowired
    private UnroutableCoordinateService unroutableCoordinateService;

    @Test
    void 짝이_하나뿐이면_아직_차단하지_않는다() {
        unroutableCoordinateService.report(BEFORE, GWIMOK, UnroutableReason.NO_ROAD_LINK);

        Set<CoordinateKey> blocked = unroutableCoordinateService.blockedPoints();

        assertFalse(blocked.contains(CoordinateKey.of(GWIMOK)), "한 구간만으로는 어느 쪽이 나쁜지 모른다");
        assertFalse(blocked.contains(CoordinateKey.of(BEFORE)));
    }

    /**
     * 진짜 못 푸는 좌표는 <b>들어오는 구간과 나가는 구간이 둘 다</b> 실패한다. 그 옆에 있었을 뿐인 좌표는
     * 짝이 하나뿐이다 — 판정이 이 차이 하나로 끝나므로 TMAP 의 한국어 메시지를 읽을 필요가 없다.
     */
    @Test
    void 서로_다른_짝으로_두_번_걸리면_그_좌표만_차단된다() {
        unroutableCoordinateService.report(BEFORE, GWIMOK, UnroutableReason.NO_ROAD_LINK);
        unroutableCoordinateService.report(GWIMOK, AFTER, UnroutableReason.NO_ROAD_LINK);

        Set<CoordinateKey> blocked = unroutableCoordinateService.blockedPoints();

        assertTrue(blocked.contains(CoordinateKey.of(GWIMOK)), "두 구간 모두에 있었다");
        assertFalse(blocked.contains(CoordinateKey.of(BEFORE)), "귀목봉과의 한 구간만 실패했다");
        assertFalse(blocked.contains(CoordinateKey.of(AFTER)), "귀목봉과의 한 구간만 실패했다");
    }

    /**
     * 같은 구간의 재실패는 새 증거가 아니다. 세면 <b>한 코스를 두 번 만드는 것만으로</b> 차단 조건이
     * 채워져, 멀쩡한 이웃까지 함께 빠진다.
     */
    @Test
    void 같은_구간이_여러_번_실패해도_증거는_하나다() {
        unroutableCoordinateService.report(BEFORE, GWIMOK, UnroutableReason.NO_ROAD_LINK);
        unroutableCoordinateService.report(BEFORE, GWIMOK, UnroutableReason.NO_ROAD_LINK);
        unroutableCoordinateService.report(GWIMOK, BEFORE, UnroutableReason.NO_ROAD_LINK);

        assertFalse(unroutableCoordinateService.blockedPoints().contains(CoordinateKey.of(GWIMOK)));
    }

    /** 방향이 반대여도 같은 구간이다 — {@code report} 가 이미 양쪽을 적었기 때문이다. */
    @Test
    void 구간을_뒤집어_보고해도_같은_구간으로_본다() {
        unroutableCoordinateService.report(GWIMOK, BEFORE, UnroutableReason.NO_ROAD_LINK);
        unroutableCoordinateService.report(BEFORE, GWIMOK, UnroutableReason.OUT_OF_BOUNDS);

        assertFalse(unroutableCoordinateService.blockedPoints().contains(CoordinateKey.of(GWIMOK)));
    }

    /**
     * 울릉군으로 적재된 장소 3건이 경도 130.9 가 아니라 128.87(동해 한복판)을 들고 있었다. 사유가 달라도
     * 결과는 같다 — 그 좌표로는 경로를 못 만든다.
     */
    @Test
    void 범위_초과도_같은_규칙으로_차단된다() {
        Coordinate wrong = new Coordinate(38.4221053, 128.8701072);

        unroutableCoordinateService.report(BEFORE, wrong, UnroutableReason.OUT_OF_BOUNDS);
        unroutableCoordinateService.report(wrong, AFTER, UnroutableReason.OUT_OF_BOUNDS);

        assertTrue(unroutableCoordinateService.blockedPoints().contains(CoordinateKey.of(wrong)));
    }

    /**
     * 저장은 {@code DECIMAL(10,7)} 이고 조회 키도 같은 자릿수다. 한쪽만 어긋나면 차단해 둔 좌표가 다음
     * 코스에서 조용히 되살아난다.
     */
    @Test
    void 저장했다_읽어도_같은_좌표로_찾힌다() {
        unroutableCoordinateService.report(BEFORE, GWIMOK, UnroutableReason.NO_ROAD_LINK);
        unroutableCoordinateService.report(GWIMOK, AFTER, UnroutableReason.NO_ROAD_LINK);

        Set<CoordinateKey> blocked = unroutableCoordinateService.blockedPoints();

        assertEquals(1, blocked.size());
        assertTrue(blocked.contains(CoordinateKey.of(GWIMOK.lat(), GWIMOK.lng())));
    }
}

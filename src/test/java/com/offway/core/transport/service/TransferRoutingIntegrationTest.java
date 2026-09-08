package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.domain.TransferHub;
import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.repository.TransitLegDurationRepository;
import com.offway.core.transport.service.dto.RegionAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직통이 없을 때 <b>경유를 잡거나 없다고 말한다</b>(#508).
 *
 * <p>예전에는 그 구간에 차가 없어도 그냥 답했다 — 서울에서 봉화까지 고속버스로 가라는 안내가 나갔는데
 * 봉화행 노선은 어디에도 없다(TAGO 실측 2026-09-08: 영주·안동·동대구 어디서도 0편, 시외 터미널 목록
 * 340개에 봉화 없음). <b>없는 길을 안내하는 것은 아무 안내도 안 하는 것보다 나쁘다.</b>
 */
@SpringBootTest
@Transactional
class TransferRoutingIntegrationTest {

    /** 무주군 — 실측에서 대전복합을 거쳐야 닿는 곳으로 나왔다. */
    private static final double MUJU_LAT = 36.0069;

    private static final double MUJU_LNG = 127.6608;

    private static final double SEOUL_LAT = 37.5547;

    private static final double SEOUL_LNG = 126.9707;

    @Autowired
    private RegionAccessService regionAccessService;

    @Autowired
    private BusTerminalResolver busTerminalResolver;

    @Autowired
    private TransitLegDurationRepository transitLegDurationRepository;

    /**
     * 허브는 좌표로 두고 수단별로 그 자리의 터미널을 푼다 — 코드 공간이 갈려 있어서다(#507).
     *
     * <p><b>두 종류를 다 본다.</b> 고속만 확인하면 시외 경유 경로는 이 검사의 보호를 못 받는다 —
     * 허브 좌표가 시외 터미널을 못 잡으면 시외로는 경유 후보가 통째로 사라지는데, 그건 조용히 일어난다.
     */
    @ParameterizedTest
    @EnumSource(BusTerminalKind.class)
    void 허브_좌표가_두_수단_모두에서_터미널로_풀린다(BusTerminalKind kind) {
        for (TransferHub hub : TransferHub.values()) {
            List<Terminal> found = busTerminalResolver.nearestWithDuplicates(
                    hub.coordinate().lat(), hub.coordinate().lng(), kind);
            assertTrue(!found.isEmpty(),
                    hub.label() + " 자리에 " + kind + " 터미널이 안 잡힌다 — 그 수단으로는 경유가 사라진다");
        }
    }

    /**
     * 직통이 <b>없는 것으로 판명된</b> 구간은 없다고 답한다.
     *
     * <p>아직 안 재본 구간은 손대지 않는다 — 모르는 것을 없다고 말하면 멀쩡한 길을 지운다.
     */
    @Test
    void 노선이_없는_것이_확인되면_없다고_답한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 6, 0);
        RegionAccess before = accessToMuju();
        blockAllDirect(kindOf(before), now);

        RegionAccess after = accessToMuju();

        assertTrue(after.status() == RegionAccess.Status.NO_ROUTE
                        || after.viaName() != null,
                "노선이 없다고 잰 구간인데 그대로 답한다 — 없는 길을 안내하게 된다: "
                        + after.status() + " via=" + after.viaName());
    }

    /** 두 구간이 다 잰 값이면 경유로 잇고, 소요시간은 합에 환승 대기를 더한다. */
    @Test
    void 두_구간이_있으면_허브를_거쳐_잇는다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 6, 0);
        RegionAccess seed = accessToMuju();
        BusTerminalKind kind = kindOf(seed);
        TransitMode mode = TransitMode.of(kind);
        Terminal from = busTerminalResolver.nearestWithDuplicates(SEOUL_LAT, SEOUL_LNG, kind).getFirst();
        Terminal to = busTerminalResolver.nearestWithDuplicates(MUJU_LAT, MUJU_LNG, kind).getFirst();
        Terminal hub = busTerminalResolver.nearestWithDuplicates(
                TransferHub.DAEJEON.coordinate().lat(), TransferHub.DAEJEON.coordinate().lng(), kind).getFirst();

        blockAllDirect(kind, now);
        measure(mode, from.code(), hub.code(), 120, now);
        measure(mode, hub.code(), to.code(), 60, now);

        RegionAccess after = accessToMuju();

        assertEquals(TransferHub.DAEJEON.label(), after.viaName(), "대전복합을 거치는 길을 못 찾았다");
        assertNotNull(after.durationMinutes());
        assertTrue(after.durationMinutes() > 180,
                "환승 대기가 안 얹혔다 — 모자라게 말하면 지킬 수 없는 코스가 된다: " + after.durationMinutes());
    }

    /** 경유는 시간표를 못 잇는다 — 두 구간의 환승 대기를 맞출 수 없어서다. 없는 것을 지어내지 않는다. */
    @Test
    void 경유일_때_시간표는_비운다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 6, 0);
        RegionAccess seed = accessToMuju();
        BusTerminalKind kind = kindOf(seed);
        TransitMode mode = TransitMode.of(kind);
        Terminal from = busTerminalResolver.nearestWithDuplicates(SEOUL_LAT, SEOUL_LNG, kind).getFirst();
        Terminal to = busTerminalResolver.nearestWithDuplicates(MUJU_LAT, MUJU_LNG, kind).getFirst();
        Terminal hub = busTerminalResolver.nearestWithDuplicates(
                TransferHub.DAEJEON.coordinate().lat(), TransferHub.DAEJEON.coordinate().lng(), kind).getFirst();
        blockAllDirect(kind, now);
        measure(mode, from.code(), hub.code(), 120, now);
        measure(mode, hub.code(), to.code(), 60, now);

        RegionAccess after = accessToMuju();

        assertTrue(after.departures().isEmpty(), "환승 대기를 모르는데 시간표를 실으면 지어낸 값이 된다");
    }

    /** 아직 안 잰 구간은 건드리지 않는다 — 모르는 것과 없는 것은 다르다. */
    @Test
    void 아직_안_잰_구간은_없다고_말하지_않는다() {
        RegionAccess access = accessToMuju();

        assertTrue(access.status() != RegionAccess.Status.NO_ROUTE,
                "재보지도 않고 없다고 하면 멀쩡한 길이 지워진다");
        assertNull(access.viaName(), "안 재본 구간에 경유를 붙이면 근거 없는 안내가 된다");
    }

    private RegionAccess accessToMuju() {
        return regionAccessService.accessTo(
                SEOUL_LAT, SEOUL_LNG, MUJU_LAT, MUJU_LNG,
                LocalDate.of(2026, 10, 15), LocalTime.of(8, 0));
    }

    private static BusTerminalKind kindOf(RegionAccess access) {
        return access.mode() == TransitMode.EXPRESS_BUS ? BusTerminalKind.EXPRESS : BusTerminalKind.INTERCITY;
    }

    /**
     * 서울 쪽 출발 후보를 <b>전부</b> 막는다.
     *
     * <p>하나만 막으면 서비스가 다른 코드로 우회한다(#507) — 그게 맞는 동작이라, 경유 경로까지 가려면
     * "이 수단으로는 어디서 타든 못 간다" 를 만들어야 한다.
     */
    private void blockAllDirect(BusTerminalKind kind, LocalDateTime now) {
        TransitMode mode = TransitMode.of(kind);
        Terminal to = busTerminalResolver.nearestWithDuplicates(MUJU_LAT, MUJU_LNG, kind).getFirst();
        for (Terminal from : busTerminalResolver.candidatesNear(SEOUL_LAT, SEOUL_LNG, kind, 8)) {
            markNoService(mode, from.code(), to.code(), now);
        }
    }

    private void markNoService(TransitMode mode, String dep, String arr, LocalDateTime now) {
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(mode, dep, arr, now));
        TransitLegDuration leg = transitLegDurationRepository.find(mode, dep, arr).orElseThrow();
        leg.measured(null, now);
        transitLegDurationRepository.save(leg);
    }

    private void measure(TransitMode mode, String dep, String arr, int minutes, LocalDateTime now) {
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(mode, dep, arr, now));
        TransitLegDuration leg = transitLegDurationRepository.find(mode, dep, arr).orElseThrow();
        leg.measured(new MeasuredLeg(minutes, 20_000, "우등"), now);
        transitLegDurationRepository.save(leg);
    }

    /**
     * <b>있는 건 다 알려준다 — 대표는 그중 하나다.</b>
     *
     * <p>{@code TransitOption} 에 소요시간 필드가 있는데 열차만 채우고 있었다. 그래서 화면이 "무엇으로
     * 갈 수 있다" 까지만 말하고 "얼마나 걸리나" 를 못 말했다 — 사용자가 수단을 고르려면 그 숫자가 있어야
     * 하는데, 대표 하나의 상태가 화면 전체를 대표하게 됐다.
     */
    @Test
    void 대안도_소요시간을_든다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 6, 0);
        RegionAccess seed = accessToMuju();
        BusTerminalKind other = kindOf(seed) == BusTerminalKind.EXPRESS
                ? BusTerminalKind.INTERCITY : BusTerminalKind.EXPRESS;
        TransitMode otherMode = TransitMode.of(other);
        Terminal from = busTerminalResolver.nearestWithDuplicates(SEOUL_LAT, SEOUL_LNG, other).getFirst();
        Terminal to = busTerminalResolver.nearestWithDuplicates(MUJU_LAT, MUJU_LNG, other).getFirst();
        measure(otherMode, from.code(), to.code(), 195, now);

        RegionAccess after = accessToMuju();

        assertTrue(
                after.alternatives().stream()
                        .filter(option -> option.mode() == otherMode)
                        .anyMatch(option -> Integer.valueOf(195).equals(option.durationMinutes())),
                "대안에 소요시간이 안 실렸다 — 화면이 수단을 고를 근거를 못 준다: "
                        + after.alternatives().stream()
                                .map(o -> o.mode() + "=" + o.durationMinutes()).toList());
    }

    /** 소요시간이 비어 있는 <b>이유</b>를 말한다 — 안 잰 것과 노선이 없는 것은 화면이 할 말이 다르다. */
    @Test
    void 대안도_노선이_없으면_그렇게_적는다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 6, 0);
        RegionAccess seed = accessToMuju();
        BusTerminalKind other = kindOf(seed) == BusTerminalKind.EXPRESS
                ? BusTerminalKind.INTERCITY : BusTerminalKind.EXPRESS;
        TransitMode otherMode = TransitMode.of(other);
        Terminal to = busTerminalResolver.nearestWithDuplicates(MUJU_LAT, MUJU_LNG, other).getFirst();
        for (Terminal from : busTerminalResolver.candidatesNear(SEOUL_LAT, SEOUL_LNG, other, 8)) {
            markNoService(otherMode, from.code(), to.code(), now);
        }

        RegionAccess after = accessToMuju();

        assertTrue(
                after.alternatives().stream()
                        .filter(option -> option.mode() == otherMode)
                        .anyMatch(option -> option.status() == RegionAccess.Status.NO_ROUTE),
                "노선이 없는 대안이 '모름' 으로 보인다 — 같은 null 이라도 이유가 다르다");
    }
}

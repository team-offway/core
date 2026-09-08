package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.repository.TransitLegDurationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 자리를 가리키는 <b>중복 터미널 코드</b>를 가려낸다(#507).
 *
 * <p>TAGO 목록에는 이름·좌표가 같은데 코드가 여러 개인 터미널이 있고(동대구 7개·전주 5개·동서울 4개·
 * 센트럴시티 2개), <b>그중 한쪽으로만 구간이 조회된다.</b> 좌표만 보면 DB 순서가 고르는데 그건 우연이다 —
 * 실측에서 {@code NAEK020}(센트럴시티)→광주는 0편, {@code NAEK021}(같은 센트럴시티)→광주는 68편인데
 * 우리는 0편 쪽을 쓰고 있었다.
 */
@SpringBootTest
@Transactional
class RoutableDepartureIntegrationTest {

    /** 서울역 — 반경 안에 서울경부·센트럴시티·동서울이 함께 잡히는 자리다. */
    private static final double SEOUL_LAT = 37.5547;

    private static final double SEOUL_LNG = 126.9707;

    @Autowired
    private BusTerminalResolver busTerminalResolver;

    @Autowired
    private TransitDurationService transitDurationService;

    @Autowired
    private TransitLegDurationRepository transitLegDurationRepository;

    /**
     * 같은 자리 묶기는 <b>이름이 같은 것</b>만이다.
     *
     * <p>옆 동네 터미널까지 묶으면 도착 지점이 지역을 벗어난다 — 도착은 지역이 정하는 것이라, 바꿀 수
     * 있는 것은 "같은 곳을 가리키는 코드" 뿐이다.
     */
    @Test
    void 같은_자리_묶음은_이름이_같은_코드만_담는다() {
        List<Terminal> group =
                busTerminalResolver.nearestWithDuplicates(SEOUL_LAT, SEOUL_LNG, BusTerminalKind.EXPRESS);

        assertTrue(!group.isEmpty(), "서울에 고속 터미널이 없다면 시드가 깨진 것이다");
        String name = group.getFirst().name();
        assertTrue(group.stream().allMatch(terminal -> name.equals(terminal.name())),
                "이름이 다른 터미널이 섞였다 — 옆 동네로 도착지가 바뀐다: "
                        + group.stream().map(Terminal::name).toList());
    }

    /** 근처의 <b>다른</b> 터미널은 출발 후보로는 필요하다 — 서울경부와 센트럴시티는 노선망이 갈린다. */
    @Test
    void 출발_후보는_근처의_다른_터미널까지_준다() {
        List<Terminal> candidates =
                busTerminalResolver.candidatesNear(SEOUL_LAT, SEOUL_LNG, BusTerminalKind.EXPRESS, 8);

        assertTrue(candidates.size() > 1,
                "서울 반경 30㎞ 안에 고속 터미널이 하나뿐일 수 없다: " + candidates.size());
        assertTrue(candidates.stream().map(Terminal::name).distinct().count() > 1,
                "이름이 다른 터미널이 후보에 없으면 노선망이 갈린 터미널을 못 고른다");
    }

    /**
     * <b>"재봤더니 없다" 와 "아직 안 쟀다" 를 가른다.</b>
     *
     * <p>이 구분이 없으면 0편으로 판명된 코드를 계속 고르게 된다 — 서울에서 나가는 버스가 통째로
     * "운행 없음" 으로 보이던 것이 그 모양이었다.
     */
    @Test
    void 재봤더니_없는_구간과_아직_안_잰_구간을_가른다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 3, 0);
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(
                TransitMode.EXPRESS_BUS, "TESTDUP1", "TESTARR9", now));
        TransitLegDuration leg = transitLegDurationRepository
                .find(TransitMode.EXPRESS_BUS, "TESTDUP1", "TESTARR9")
                .orElseThrow();

        assertTrue(!transitDurationService.knownUnroutable(TransitMode.EXPRESS_BUS, "TESTDUP1", "TESTARR9"),
                "아직 안 쟀는데 '없다' 로 보면 되는 코드를 후보에서 빼버린다");

        leg.measured(null, now); // 재봤더니 운행이 없다
        transitLegDurationRepository.save(leg);

        assertTrue(transitDurationService.knownUnroutable(TransitMode.EXPRESS_BUS, "TESTDUP1", "TESTARR9"),
                "0편으로 판명된 코드를 계속 고르면 같은 오답이 굳는다");
    }

    /** 잰 값이 있으면 그 코드가 쓸 수 있는 것으로 나온다 — 우선순위 1순위의 근거다. */
    @Test
    void 다니는_것이_확인된_구간은_소요시간을_준다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 3, 0);
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(
                TransitMode.EXPRESS_BUS, "TESTDUP2", "TESTARR9", now));
        TransitLegDuration leg = transitLegDurationRepository
                .find(TransitMode.EXPRESS_BUS, "TESTDUP2", "TESTARR9")
                .orElseThrow();
        leg.measured(new MeasuredLeg(210, 34_000, "우등"), now);
        transitLegDurationRepository.save(leg);

        assertEquals(
                210,
                transitDurationService
                        .measuredMinutes(TransitMode.EXPRESS_BUS, "TESTDUP2", "TESTARR9")
                        .orElseThrow());
    }
}

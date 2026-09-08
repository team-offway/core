package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.repository.TransitLegDurationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 버스 구간 후보 사전 적재(#450).
 *
 * <p>부팅 시 {@code ApplicationReadyEvent} 로 이미 돌았으므로, 여기서는 <b>그 결과</b>를 본다.
 */
@SpringBootTest
class TransitLegCandidateSeedIntegrationTest {

    /**
     * 사전 적재로 만들어지는 구간 수 — <b>33,448</b>(2026-09-06 실측).
     *
     * <p>고속 × 도착 82곳 + 시외 × 도착 74곳이다(자기 자신 제외). 고속이 74 에서 82 로 는 것은 같은
     * 자리를 가리키는 <b>중복 코드</b>를 함께 넣기 때문이다(#507) — 그중 한쪽으로만 구간이 조회되는데,
     * 하나만 넣으면 배치가 나머지를 재볼 기회 자체가 없어 되는 코드가 있어도 "운행 없음" 으로 남는다. 도착은 인구감소지역 89곳이
     * 실제로 쓰는 터미널만 센다 — 전국을 도착지로 두면 조합이 폭발하는데, 우리가 코스를 만드는 곳은
     * 인구감소지역뿐이다.
     *
     * <p><b>이 값이 크게 흔들리면 시드가 바뀐 것이다.</b> 터미널 좌표가 사라지면 출발 후보가 줄고,
     * 도착 해석이 바뀌면 곱해지는 쪽이 바뀐다. 하한만 보면 그 회귀를 놓친다.
     */
    private static final int EXPECTED_CANDIDATES = 35_504;

    /** 지연 생성으로 이미 있던 행까지 더해 이보다 적을 수는 없다 — 사전 적재가 통째로 안 돌면 여기서 걸린다. */
    private static final int MIN_ROWS = EXPECTED_CANDIDATES;

    /** 배치가 한 회차에 가져가는 것보다 넉넉히 — 대상이 한 페이지도 안 차면 적재가 안 된 것이다. */
    private static final int PENDING_PAGE = 1_000;

    @Autowired
    private TransitLegDurationRepository transitLegDurationRepository;

    @Autowired
    private TransitLegCandidateSeeder seeder;

    @Test
    void 부팅에_버스_구간_후보가_만들어진다() {
        List<TransitLegDuration> all = transitLegDurationRepository.findAll();

        assertTrue(all.size() >= MIN_ROWS,
                "구간 후보가 적재되지 않았습니다 — 버스는 조회창 밖 날짜를 물어볼 수 없어 이게 없으면 영원히 빈다: "
                        + all.size());
    }

    /**
     * <b>고속과 시외를 섞어 만들지 않는다.</b> 코드 공간이 겹치지 않아, 출발은 고속·도착은 시외로 물으면
     * 제공기관이 알 수 없는 코드로 읽는다.
     */
    @Test
    void 수단별로_같은_종류끼리만_짝짓는다() {
        Map<TransitMode, List<TransitLegDuration>> byMode = transitLegDurationRepository.findAll().stream()
                .collect(Collectors.groupingBy(TransitLegDuration::getMode));

        assertTrue(byMode.getOrDefault(TransitMode.EXPRESS_BUS, List.of()).stream()
                        .allMatch(leg -> leg.getDepCode().startsWith("NAEK") && leg.getArrCode().startsWith("NAEK")),
                "고속 구간에 시외 코드가 섞였습니다");
        assertTrue(byMode.getOrDefault(TransitMode.INTERCITY_BUS, List.of()).stream()
                        .allMatch(leg -> leg.getDepCode().startsWith("NAI") && leg.getArrCode().startsWith("NAI")),
                "시외 구간에 고속 코드가 섞였습니다");
    }

    /** 출발과 도착이 같은 구간은 만들지 않는다 — 물어봐야 답이 없다. */
    @Test
    void 자기_자신으로_가는_구간은_없다() {
        long selfLoops = transitLegDurationRepository.findAll().stream()
                .filter(leg -> leg.getDepCode().equals(leg.getArrCode()))
                .count();

        assertEquals(0, selfLoops, "출발과 도착이 같은 구간이 있습니다");
    }

    /**
     * <b>다시 돌려도 안 늘어난다.</b> 재배포마다 치르는 값이 이쪽이다 — 매번 3만 건을 다시 넣으면 배포가
     * 그만큼 느려지고, 유니크 제약에 걸린 예외가 로그를 뒤덮는다.
     */
    @Test
    void 다시_돌려도_같은_구간을_또_넣지_않는다() {
        int before = transitLegDurationRepository.findAll().size();

        seeder.seed();

        assertEquals(before, transitLegDurationRepository.findAll().size(),
                "재실행이 구간을 또 넣었습니다 — 재배포마다 적재가 반복됩니다");
    }

    /** 구간이 코드 짝으로 유일해야 한다 — 중복이면 배치가 같은 것을 두 번 잰다. */
    @Test
    void 같은_구간이_중복되지_않는다() {
        List<TransitLegDuration> all = transitLegDurationRepository.findAll();

        Set<String> keys = all.stream()
                .map(leg -> leg.getMode() + "|" + leg.getDepCode() + "|" + leg.getArrCode())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertEquals(all.size(), keys.size(), "같은 구간이 여러 행으로 들어 있습니다");
    }

    /**
     * 89곳이 <b>어느 수단으로든</b> 도착지로 잡히는지 — 도착 해석이 조용히 줄면 여기서 걸린다.
     *
     * <p>구간의 도착 코드 집합이 곧 "우리가 내리는 곳" 이다.
     */
    @Test
    void 도착지가_두_수단_모두에_잡힌다() {
        Map<TransitMode, Long> arrivals = transitLegDurationRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        TransitLegDuration::getMode,
                        Collectors.collectingAndThen(
                                Collectors.mapping(TransitLegDuration::getArrCode, Collectors.toSet()),
                                set -> (long) set.size())));

        // 정류소는 도착에서도 뺀다(#446·#450) — 구간 조회가 터미널 코드를 전제한다. 시외가 77 에서 74 로
        // 준 것이 그 결과다(세 곳은 최근접이 정류소였다).
        //
        // 고속이 74 에서 82 로 는 것은 같은 자리의 중복 코드를 함께 넣기 때문이다(#507). 시외는 그대로인데,
        // 89곳이 쓰는 시외 터미널에는 중복이 없다 — 중복은 고속 목록 쪽에 몰려 있다.
        assertEquals(82L, arrivals.get(TransitMode.EXPRESS_BUS), "고속 도착 터미널 수");
        assertEquals(74L, arrivals.get(TransitMode.INTERCITY_BUS), "시외 도착 터미널 수");
    }

    /**
     * 후보는 <b>"아직 안 잼"</b> 으로 들어간다 — 배치가 그것을 보고 대상으로 삼는다.
     *
     * <p><b>배치가 쓰는 질의 그대로 묻는다.</b> 전체 행을 세면 이 컨텍스트를 공유하는 다른 테스트가 몇 건을
     * 재 놓아 수가 흔들린다. 확인하려는 것은 총량이 아니라 "배치가 잴 것을 찾는가" 다.
     */
    @Test
    void 후보는_아직_안_잰_상태로_들어간다() {
        List<TransitLegDuration> pending =
                transitLegDurationRepository.pending(PENDING_PAGE, LocalDateTime.now());

        assertEquals(PENDING_PAGE, pending.size(), "배치가 잴 대상을 한 페이지도 못 채웁니다");
        assertTrue(pending.stream().allMatch(leg -> leg.getMeasuredAt() == null),
                "안 잰 구간이 먼저 와야 합니다 — 미운행 재측정이 신규 적재를 밀어냅니다");
    }
}

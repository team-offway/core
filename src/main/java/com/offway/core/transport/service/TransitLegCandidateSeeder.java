package com.offway.core.transport.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.transport.domain.BusTerminal;
import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.domain.TransferHub;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.repository.BusTerminalRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 버스 구간의 <b>후보를 미리 만들어 둔다</b>(#450).
 *
 * <p><b>왜 필요한가.</b> 출발 지점의 원칙은 "목적지와 연결되는 가장 가까운 터미널" 이다. 열차는 이미
 * 그렇게 한다(#435) — TAGO 열차 API 가 날짜를 가리지 않아 그 자리에서 물어볼 수 있다. 버스는 못 한다.
 * 구간 조회가 <b>오늘~+2일만</b> 답하는데 우리는 연차 기반으로 다음 달 코스를 짜므로, 대부분의 여행일이
 * 조회창 밖이다.
 *
 * <p>그래서 {@link TransitDurationRefreshService} 가 조회창 안에서 미리 재 둔다. 그런데 <b>잴 대상이
 * 생기질 않았다</b> — {@code transit_leg_duration} 행은 누가 그 조합으로 코스를 만들 때만 만들어지고
 * (지연 생성), 배치는 이미 생긴 행만 채운다. 아무도 안 만든 조합은 영원히 안 생긴다. 운영 실측(2026-09-05)
 * 으로 전국에 <b>4건</b>이었다.
 *
 * <p><b>그래서 여기서 만든다.</b> 우리가 쓸 조합은 대체로 정해져 있다 — 좌표를 가진 터미널에서 출발해
 * 인구감소지역 89곳의 도착 터미널로 간다. 배치가 하루치씩 긁으면 시간이 지나며 "어느 터미널에서 어디로
 * 가는가" 지도가 쌓이고, 그게 쌓이면 버스도 열차처럼 고를 수 있다.
 *
 * <p><b>소요시간이 없어도 된다.</b> 구간이 존재한다는 사실 자체가 "여기서 탈 수 있다" 다.
 *
 * <h2>부팅 비용</h2>
 *
 * <p>첫 부팅에만 든다. 두 번째부터는 이미 있는 조합을 빼고 나면 넣을 것이 없어 <b>SELECT 한 번</b>으로
 * 끝난다 — 재배포마다 치르는 값이 그쪽이다(CLAUDE.md §운영에서 버티는가).
 *
 * <p>실패해도 부팅을 막지 않는다. 후보가 없으면 지금까지처럼 지연 생성으로 돌아갈 뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransitLegCandidateSeeder {

    /** 한 번에 넣는 행 수 — 3만 건을 한 문장으로 밀면 MySQL 의 max_allowed_packet 에 걸린다. */
    private static final int INSERT_BATCH = 1_000;

    /** 여행도 배차도 한국 기준이다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RegionQuery regionQuery;
    private final BusTerminalRepository busTerminalRepository;
    private final BusTerminalResolver busTerminalResolver;
    private final TransitLegCandidatePersistenceService persistenceService;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        long startedAt = System.nanoTime();
        try {
            List<Candidate> candidates = candidates();
            if (candidates.isEmpty()) {
                log.info("버스 구간 후보 — 만들 조합이 없습니다(터미널·지역 마스터를 확인하세요)");
                return;
            }
            int inserted = insertMissing(candidates);
            log.info("버스 구간 후보 적재 — 후보 {}건 · 새로 넣음 {}건 · {}ms",
                    candidates.size(), inserted, elapsedMillis(startedAt));
        } catch (RuntimeException e) {
            // ApplicationReadyEvent 리스너에서 던진 예외는 부팅을 실패시킨다. 후보가 없으면 지금까지처럼
            // 지연 생성으로 돌아갈 뿐이라, 이것 때문에 서비스가 안 뜨는 쪽이 훨씬 나쁘다.
            log.error("버스 구간 후보 적재 실패 — 지연 생성으로 돌아갑니다", e);
        }
    }

    /**
     * 만들 조합 — <b>수단별로</b> 출발 터미널 × 도착 터미널.
     *
     * <p>고속({@code NAEK...})과 시외({@code NAI...})는 코드 공간이 겹치지 않아, 섞어 물으면 제공기관이
     * 알 수 없는 코드로 읽는다. 같은 종류끼리만 짝짓는다.
     *
     * <p>도착은 <b>89곳이 실제로 쓰는 터미널</b>만이다. 전국 터미널을 도착지로 두면 조합이 폭발하는데,
     * 우리가 코스를 만드는 곳은 인구감소지역뿐이다.
     */
    private List<Candidate> candidates() {
        List<Region> regions = regionQuery.all();
        List<BusTerminal> terminals = busTerminalRepository.findAll();
        if (regions.isEmpty() || terminals.isEmpty()) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (BusTerminalKind kind : BusTerminalKind.values()) {
            Set<String> arrivals = arrivalCodes(regions, kind);
            if (arrivals.isEmpty()) {
                continue;
            }
            TransitMode mode = TransitMode.of(kind);
            // **허브도 도착지로 넣는다**(#508). 경유는 출발→허브·허브→도착 두 구간이 모두 잰 값일 때만
            // 성립하는데, 앞 구간은 도착지가 지역이 아니라 허브라 여기 없으면 영영 안 재진다.
            Set<String> destinations = new LinkedHashSet<>(arrivals);
            destinations.addAll(hubCodes(kind));
            for (BusTerminal origin : terminals) {
                // 정류소는 출발지로 두지 않는다 — 특정 노선만 서고, 구간 조회도 터미널 코드를 전제한다(#446).
                if (origin.getKind() != kind || !origin.hasCoordinate() || !origin.isTerminal()) {
                    continue;
                }
                for (String arrival : destinations) {
                    if (!origin.getCode().equals(arrival)) {
                        candidates.add(new Candidate(mode, origin.getCode(), arrival));
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * 89곳이 그 종류로 내리는 터미널 코드 — 대표 선정과 <b>같은 규칙</b>으로 푼다.
     *
     * <p><b>같은 자리의 중복 코드도 함께 넣는다</b>(#507). TAGO 목록에는 이름·좌표가 같은데 코드가 여러
     * 개인 터미널이 있고 그중 한쪽으로만 구간이 조회된다. 최근접 하나만 넣으면 배치가 나머지를 재볼
     * 기회 자체가 없어, 되는 코드가 있는데도 "운행 없음" 으로 남는다.
     *
     * <p>근처의 <b>다른</b> 터미널까지 넣지는 않는다 — 도착 지점은 지역이 정하는 것이라 옆 동네로
     * 바꿔치면 안 된다. 늘리는 것은 "같은 곳을 가리키는 코드" 뿐이다.
     */
    /** 경유 후보 자리의 그 종류 터미널 코드(#508). 코드가 여럿이면 전부 — 되는 쪽을 배치가 가린다. */
    private Set<String> hubCodes(BusTerminalKind kind) {
        Set<String> codes = new LinkedHashSet<>();
        for (TransferHub hub : TransferHub.values()) {
            busTerminalResolver
                    .nearestWithDuplicates(hub.coordinate().lat(), hub.coordinate().lng(), kind).stream()
                    .filter(Terminal::isTerminal)
                    .map(Terminal::code)
                    .forEach(codes::add);
        }
        return codes;
    }

    private Set<String> arrivalCodes(List<Region> regions, BusTerminalKind kind) {
        Set<String> codes = new LinkedHashSet<>();
        for (Region region : regions) {
            busTerminalResolver
                    .nearestWithDuplicates(region.getLat(), region.getLng(), kind).stream()
                    // **도착도 터미널이어야 한다.** resolver 는 반경 안에 터미널이 없으면 정류소를 준다(#446).
                    // 구간 조회가 터미널 코드를 전제하므로 정류소 코드로 물으면 답이 없다 — 출발 쪽을
                    // 거르면서 도착 쪽을 안 걸렀다.
                    .filter(Terminal::isTerminal)
                    .map(Terminal::code)
                    .forEach(codes::add);
        }
        return codes;
    }

    private int insertMissing(List<Candidate> candidates) {
        Set<String> existing = persistenceService.existingKeys();
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        List<Candidate> missing = candidates.stream()
                .filter(candidate -> !existing.contains(candidate.key()))
                .toList();
        if (missing.isEmpty()) {
            return 0;
        }
        log.info("버스 구간 후보 — {}건을 새로 넣습니다(이미 있는 것 {}건)", missing.size(), existing.size());
        int inserted = 0;
        for (int from = 0; from < missing.size(); from += INSERT_BATCH) {
            List<Candidate> slice = missing.subList(from, Math.min(from + INSERT_BATCH, missing.size()));
            inserted += persistenceService.insert(slice, now);
        }
        return inserted;
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    /** 만들 구간 하나. {@code key} 는 {@code transit_leg_duration} 의 유니크 제약과 같은 축이다. */
    public record Candidate(TransitMode mode, String depCode, String arrCode) {

        public String key() {
            return mode + "|" + depCode + "|" + arrCode;
        }
    }
}

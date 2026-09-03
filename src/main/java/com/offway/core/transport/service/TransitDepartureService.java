package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCachePolicy;
import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.infrastructure.tago.TransitLegClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 버스·여객선 <b>시간표</b>(#414) — 몇 시 차가 있는지.
 *
 * <h2>왜 소요시간과 따로인가</h2>
 *
 * <p>구간 소요시간({@code TransitDurationService})은 DB 에서 읽어 요청 경로가 외부를 안 친다. 시간표는 그럴
 * 수 없다 — 날짜마다 다르고, 그 날짜를 물을 수 있는 창이 <b>오늘~+2일</b>(여객선 +7일)뿐이라 미리 담아 둘
 * 수가 없다. 담아 둬도 다음 날이면 쓸모가 없다.
 *
 * <h2>부르는 조건을 여기서 닫는다</h2>
 *
 * <p><b>조회창 밖이면 아예 안 부른다.</b> 물어봐야 빈 결과가 오고 한도만 깎인다. 연차 기준으로 다음 달
 * 코스를 짜는 서비스라 대부분의 코스가 창 밖이고, 그때 이 서비스는 외부를 한 번도 안 친다.
 *
 * <p>창 안일 때는 <b>코스 하나에 한 번</b>이다 — 대표 수단의 구간 하나만 묻는다. 대안 수단은 묻지 않는다.
 *
 * <h2>캐시</h2>
 *
 * <p>{@code TrainRouteService} 와 같은 모양이다. 같은 (수단·구간·날짜)를 다시 열면 외부를 안 친다 — 여행
 * 직전 코스는 자주 열리는 화면이라 이 효과가 크다.
 *
 * <p>TTL 이 열차보다 짧다. 열차 시간표는 하루 단위로 확정이지만 버스·여객선은 <b>증편·결항이 당일에</b>
 * 붙는다. 그래도 분 단위로 바뀌지는 않아 한 시간이면 화면이 크게 틀리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitDepartureService {

    /** 증편·결항이 당일에 붙어 열차(6h)보다 짧게 둔다. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /** 조회 실패 시 짧은 재시도 — 빈 목록을 성공 TTL 로 누르면 그 화면이 한 시간 동안 시간표 없이 뜬다. */
    private static final Duration RETRY_TTL = Duration.ofMinutes(5);

    /**
     * 보관할 (수단·구간·날짜) 조합 수.
     *
     * <p><b>키에 날짜가 들어가 키 공간이 시간과 함께 자란다</b> — 지난 날짜 엔트리는 다시 안 쓰이는데
     * 상한이 없으면 영원히 남는다. 조회창이 며칠뿐이라 열차보다 작게 잡아도 넉넉하다.
     */
    private static final int MAX_CACHED_ROUTES = 500;

    /** loader 가 TAGO 단일 호출(timeout 6초)이라 여유 1초를 얹었다. */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    private final TransitLegClient transitLegClient;
    private final ExternalApiCachePolicy cachePolicy;

    private final ExternalDataCache<String, List<Departure>> cache =
            new ExternalDataCache<>(MAX_CACHED_ROUTES, FIRST_LOAD_WAIT, this::cacheEnabled);

    /**
     * 그 날짜 그 구간의 운행 편 — <b>조회창 밖이면 빈 목록</b>이다(외부를 안 친다).
     *
     * @param today 창을 재는 기준일. 호출자가 서비스 시간대로 계산한 값을 넘긴다
     */
    public List<Departure> departures(
            TransitMode mode, String depCode, String arrCode, LocalDate date, LocalDate today) {
        if (outOfWindow(mode, date, today)) {
            return List.of();
        }
        String key = mode.name() + "|" + depCode + "|" + arrCode + "|" + date;
        return cache.get(key, (k, stale) -> {
            List<Departure> fresh = transitLegClient.departures(mode, depCode, arrCode, date);
            if (fresh.isEmpty()) {
                // 빈 결과를 성공 TTL 로 누르지 않는다. "그 날짜에 운행이 없다" 와 "못 물었다" 가 여기서는
                // 같은 모양이라, 길게 굳히면 조회가 잠깐 실패한 날의 화면이 한 시간 동안 시간표를 잃는다.
                return new Loaded<>(stale != null && !stale.isEmpty() ? stale : List.of(), RETRY_TTL);
            }
            return new Loaded<>(fresh, CACHE_TTL);
        }, List.of());
    }

    /**
     * 이 날짜를 물어볼 수 있나.
     *
     * <p>창 밖을 물으면 <b>빈 결과가 오고 한도만 깎인다</b>. 판정을 수단이 소유하므로
     * ({@link TransitMode#lookaheadDays}) 여기서 날짜 수를 다시 적지 않는다.
     */
    private static boolean outOfWindow(TransitMode mode, LocalDate date, LocalDate today) {
        if (date.isBefore(today)) {
            return true;
        }
        return ChronoUnit.DAYS.between(today, date) >= mode.lookaheadDays();
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }

    /**
     * 캐시를 지금 써도 되나(#403).
     *
     * <p>이 캐시는 <b>수단 셋을 함께</b> 담는데({@code ExternalDataCache} 는 스위치를 하나만 받는다) 스위치는
     * API 별이다. 그래서 <b>하나라도 꺼져 있으면 캐시를 안 쓴다</b> — 켜진 쪽까지 실호출로 도는 대가를
     * 치르지만, 반대(끈 API 의 값이 캐시에서 나가는 것)는 스위치가 안 듣는 것이라 더 나쁘다.
     *
     * <p>람다로 필드를 직접 읽지 않는 이유는 {@code TrainRouteService} 와 같다 — 캐시 필드의 초기화식이
     * 생성자보다 먼저 돈다.
     */
    private boolean cacheEnabled() {
        return cachePolicy.cacheEnabled(ExternalApi.EXPRESS_BUS_INFO)
                && cachePolicy.cacheEnabled(ExternalApi.INTERCITY_BUS_INFO)
                && cachePolicy.cacheEnabled(ExternalApi.SHIP_INFO);
    }
}

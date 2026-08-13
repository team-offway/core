package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.transport.domain.BusCoverage;
import com.offway.core.transport.domain.BusStopAccess;
import com.offway.core.transport.infrastructure.tago.BusStopClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대중교통 접근성 조회 — transport 가 다른 도메인(trip·itinerary)에 노출하는 공개 서비스. 좌표 주변에 버스 정류소가 있는지를
 * {@link BusStopAccess}(있음/주변에 없음/미커버/조회 불가)로 준다.
 *
 * <p>여행 <b>계획 시점</b>에 쓰는 정보다. 정류소 위치는 잘 바뀌지 않아 미래 날짜 코스에도 유효하다. 반대로 "몇 시에 오는가"는
 * 이 API 가 답하지 못하므로 {@link BusArrivalService}(실시간, 여행 당일)와 역할이 다르다.
 *
 * <p><b>정류소를 묻기 전에 커버 여부를 먼저 판별한다.</b> TAGO 시내버스는 138개 지자체만 담아, 미커버 지역은 정상 응답 +
 * 빈 결과로 온다 — 그대로 두면 "정선에 버스가 없다" 는 틀린 안내가 된다. 커버 목록({@code getCtyCodeList})으로 걸러
 * {@code NotCovered} 를 돌려주고, 미커버면 정류소 호출 자체를 아낀다.
 *
 * <p>TAGO 는 느리고 쿼터가 있어 {@link ExternalDataCache} 로 감싼다. 정류소는 거의 안 변하니 길게(24h) 캐시하고, 조회
 * 실패는 짧게(5분) 잡아 재시도를 유도하되 마지막으로 알던 결과를 계속 내려준다(stale-while-error). 커버 목록도 같다.
 */
@Service
@RequiredArgsConstructor
public class BusAccessService {

    /** 정류소 위치는 거의 변하지 않아 길게 캐시한다. */
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /** 조회 불가(실패) 시 짧은 재시도 TTL. */
    private static final Duration RETRY_TTL = Duration.ofMinutes(5);

    /**
     * 캐시 키 좌표 정밀도(소수 4자리 ≈ 11m). 원좌표를 그대로 키로 쓰면 미세하게 다른 좌표마다 키가 생겨 캐시가 사실상
     * 무력화되고 맵이 무한히 커진다.
     */
    private static final String KEY_FORMAT = "%.4f|%.4f";

    /**
     * 보관할 좌표 수. 키가 좌표라 <b>키 공간이 무한</b>하다 — 사용자가 지도를 움직이는 만큼 새 격자가 생긴다. TTL(24시간)이
     * 길어 회전이 느리므로 상한이 유일한 방어선이다. 89개 인구감소지역과 그 주변 관광지를 넉넉히 덮는 크기로 잡았다.
     */
    private static final int MAX_CACHED_COORDINATES = 2_000;

    /** loader 가 TAGO 정류소 <b>단일 호출</b>(timeout 6초)이라 여유 1초를 얹었다. */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    /** 커버 목록은 키가 하나다 — 전국 단위 목록이라 지역별로 나뉘지 않는다. */
    private static final String COVERAGE_KEY = "tago-covered-cities";

    private static final int MAX_CACHED_COVERAGE = 1;

    private final BusStopClient busStopClient;
    private final ExternalDataCache<String, BusStopAccess> cache = new ExternalDataCache<>(MAX_CACHED_COORDINATES, FIRST_LOAD_WAIT);
    private final ExternalDataCache<String, Optional<BusCoverage>> coverageCache =
            new ExternalDataCache<>(MAX_CACHED_COVERAGE, FIRST_LOAD_WAIT);

    /**
     * 좌표 주변 버스 정류소 조회 결과. 시군구는 <b>커버 여부 판별</b>에 쓴다 — 좌표만으로는 어느 지자체인지 알 수 없다.
     *
     * @param sido 시도명(예: {@code 강원특별자치도}) — 동명 시군구를 갈라내는 데 필요하다
     * @param sigungu 시군구명(예: {@code 정선군})
     */
    public BusStopAccess nearbyStops(String sido, String sigungu, double lat, double lng) {
        Optional<BusCoverage> coverage = coveredCities();
        if (coverage.isEmpty()) {
            // 커버 여부를 모르는 채 빈 결과를 "정류소 없음"으로 안내하면 틀린 말이 될 수 있다 — 조용히 폴백한다.
            return new BusStopAccess.Unavailable();
        }
        if (!coverage.get().covers(sido, sigungu)) {
            // 조회해봐야 정상 응답 + 빈 결과라 "정류소 없음"으로 오인된다. 호출 자체를 아낀다.
            return new BusStopAccess.NotCovered();
        }
        return cache.get(
                cacheKey(lat, lng),
                (key, stale) -> {
                    BusStopAccess fresh = busStopClient.nearbyStops(lat, lng);
                    if (fresh instanceof BusStopAccess.Unavailable) {
                        // 조회 실패 — 마지막으로 알던 결과(있음/없음)가 있으면 유지한다. 정류소는 잘 안 변해 오래된 값도 여전히 맞다.
                        BusStopAccess fallback =
                                stale != null && !(stale instanceof BusStopAccess.Unavailable) ? stale : fresh;
                        return new Loaded<>(fallback, RETRY_TTL);
                    }
                    return new Loaded<>(fresh, CACHE_TTL); // Available·NoStopNearby
                },
                new BusStopAccess.Unavailable());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
        coverageCache.evictAll();
    }

    /** TAGO 가 담는 지자체 목록. 138곳 고정에 거의 변하지 않아 길게 캐시한다. 조회 실패면 빈 Optional. */
    private Optional<BusCoverage> coveredCities() {
        return coverageCache.get(
                COVERAGE_KEY,
                (key, stale) -> {
                    Optional<BusCoverage> fresh = busStopClient.coveredCities();
                    if (fresh.isEmpty()) {
                        // 조회 실패 — 마지막으로 알던 목록이 있으면 유지한다. 지자체 구성은 거의 안 변해 오래된 값도 맞다.
                        Optional<BusCoverage> fallback = stale != null && stale.isPresent() ? stale : fresh;
                        return new Loaded<>(fallback, RETRY_TTL);
                    }
                    return new Loaded<>(fresh, CACHE_TTL);
                },
                Optional.empty());
    }

    private static String cacheKey(double lat, double lng) {
        // Locale.ROOT 고정 — 기본 로케일에 따라 소수점이 쉼표가 되면 키가 환경마다 달라진다.
        return String.format(Locale.ROOT, KEY_FORMAT, lat, lng);
    }
}

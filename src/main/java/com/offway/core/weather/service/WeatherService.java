package com.offway.core.weather.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.domain.MidLandRegion;
import com.offway.core.weather.domain.MidTermOutlook;
import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.infrastructure.kma.MidTermForecastClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 날씨 조회 — weather 가 다른 도메인(trip 지역·itinerary 코스)에 노출하는 공개 서비스. 좌표·날짜의 날씨 요약을 준다. 외부(기상청)
 * 호출은 read-timeout 이 있어 트랜잭션 밖에서 쓴다(부가 정보라 실패 시 빈 값).
 *
 * <p><b>여행까지 남은 일수로 예보 종류가 갈린다</b>(#129). OffWay 는 연차 기반이라 미래 날짜 조회가 흔한데, 단기예보만으로는
 * 사흘 뒤까지밖에 답하지 못한다.
 *
 * <table>
 *   <caption>남은 일수별 담당</caption>
 *   <tr><th>남은 일수</th><th>담당</th><th>주는 것</th></tr>
 *   <tr><td>0~3일</td><td>단기예보(좌표 격자)</td><td>기온·하늘상태·강수확률</td></tr>
 *   <tr><td>4~10일</td><td>중기예보(광역 구역)</td><td>하늘상태·강수확률 (기온은 미지원)</td></tr>
 *   <tr><td>11일 이상</td><td>없음</td><td>빈 값 — 예보가 존재하지 않는 구간이다(#133)</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    /** 단기예보가 최저·최고기온까지 온전히 답하는 마지막 날. 실측으로 확인했다(E2E 로 고정). */
    private static final int SHORT_TERM_LAST_DAY = 3;

    /** 발표일·발표시각이 KST 기준이라 "오늘" 판정도 KST 로 한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 중기예보 캐시 TTL. 발표가 하루 두 번(06·18시)이라 그 간격보다 짧게 둬 다음 발표를 놓치지 않는다.
     *
     * <p>중기예보는 열흘 앞을 보는 값이라 몇 시간 사이에 뒤집히지 않는다. 짧게 잡을수록 이득 없이 쿼터만 쓴다.
     */
    private static final Duration MID_TERM_TTL = Duration.ofHours(6);

    /** 조회 실패 시 짧은 재시도 TTL — 실패를 성공만큼 오래 붙들지 않는다. */
    private static final Duration MID_TERM_RETRY_TTL = Duration.ofMinutes(10);

    /** 키가 구역 코드라 상한이 그 개수로 고정된다 — 무한히 늘 여지가 없다. */
    private static final int MAX_CACHED_REGIONS = MidLandRegion.values().length;

    /** loader 가 중기예보 단일 호출(timeout 6초)이라 여유 1초를 얹었다. */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    private final KmaWeatherClient kmaWeatherClient;
    private final MidTermForecastClient midTermForecastClient;

    /**
     * 중기예보 캐시. 단기예보는 좌표 격자라 키가 무한히 늘지만, 중기는 <b>광역 구역 열 개가 전부</b>라 캐시가 자연스럽다.
     */
    private final ExternalDataCache<MidLandRegion, Optional<MidTermOutlook>> midTermCache =
            new ExternalDataCache<>(MAX_CACHED_REGIONS, FIRST_LOAD_WAIT);

    /**
     * 좌표 지점의 해당 날짜 날씨(없으면 빈 Optional).
     *
     * @param sido 중기예보 구역을 고르는 데 쓴다. 모르면 null — 나흘 뒤부터는 답하지 못한다
     * @param sigungu 강원 영동·영서를 가르는 데 쓴다(그 외 시도는 무관)
     */
    public Optional<DailyWeather> dailyWeather(
            double lat, double lng, String sido, String sigungu, LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        long daysAhead = java.time.temporal.ChronoUnit.DAYS.between(today, date);
        if (daysAhead <= SHORT_TERM_LAST_DAY) {
            // 지난 날짜도 여기로 온다 — 단기예보가 빈 값을 주므로 별도 분기가 필요 없다.
            return kmaWeatherClient.dailyForecast(lat, lng, date);
        }
        if (!MidTermOutlook.covers(today, date)) {
            // 열흘 밖 — 예보가 존재하지 않는 구간이다. 지어내지 않고 비운다(#133 에서 평년값으로 채운다).
            return Optional.empty();
        }
        return midTermWeather(sido, sigungu, date);
    }

    /**
     * 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용.
     *
     * <p>단기예보 캐시는 어댑터가 들고 있어(응답 하나가 5일치를 담는 이 API 만의 사정) 포트를 통해 함께 비운다.
     * 여기서 빠뜨리면 "캐시를 비웠는데 옛 예보가 계속 나온다" 가 된다.
     */
    public void evictCache() {
        midTermCache.evictAll();
        kmaWeatherClient.evictCache();
    }

    private Optional<DailyWeather> midTermWeather(String sido, String sigungu, LocalDate date) {
        Optional<MidLandRegion> region = MidLandRegion.of(sido, sigungu);
        if (region.isEmpty()) {
            // degrade 사유를 남긴다 — 시도명이 바뀌면(특별자치도 전환 등) 조용히 날씨가 사라진다.
            log.warn("중기예보 구역을 찾지 못했습니다 — 날씨 생략 sido={} sigungu={}", sido, sigungu);
            return Optional.empty();
        }
        return outlookOf(region.get()).flatMap(outlook -> outlook.on(date));
    }

    /**
     * 중기예보 캐시를 미리 데운다 — 키 공간이 {@link MidLandRegion} 10개로 <b>완전히 고정</b>이라 워밍이 성립한다.
     *
     * <p>안 데우면 나흘 뒤 이후 날씨를 처음 묻는 사용자가 콜드 미스를 그대로 떠안는다(첫 적재 대기 상한까지).
     * 10개뿐이라 워밍 비용도 작다.
     */
    public void warmMidTerm() {
        for (MidLandRegion region : MidLandRegion.values()) {
            outlookOf(region);
        }
    }

    private Optional<MidTermOutlook> outlookOf(MidLandRegion region) {
        return midTermCache.get(
                region,
                (key, stale) -> {
                    Optional<MidTermOutlook> fresh = midTermForecastClient.outlook(key);
                    if (fresh.isEmpty()) {
                        // 빈 응답을 성공 TTL 로 굳히지 않는다 — 실패와 결과가 같으므로 짧게 잡아 재시도를 유도한다.
                        // 직전 정상값이 있으면 그걸 유지한다(stale-while-error): 예보는 하루 두 번 갱신이라
                        // 몇 시간 지난 값도 여전히 맞는데, 버리면 그 사이 날씨가 통째로 사라진다.
                        log.warn("중기예보가 비어 왔습니다 — 직전 값 유지 region={}", key);
                        return new Loaded<>(stale != null ? stale : fresh, MID_TERM_RETRY_TTL);
                    }
                    return new Loaded<>(fresh, MID_TERM_TTL);
                },
                Optional.empty());
    }
}

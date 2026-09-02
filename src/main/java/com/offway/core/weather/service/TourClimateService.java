package com.offway.core.weather.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCachePolicy;
import com.offway.core.weather.domain.SigunguKey;
import com.offway.core.weather.domain.TourClimateIndex;
import com.offway.core.weather.infrastructure.kma.TourClimateIndexClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관광기후지수 조회(#130) — weather 가 trip·itinerary 에 노출하는 공개 서비스. "그날 그 지역이 관광하기 좋은가" 를
 * 기상청 등급으로 답한다.
 *
 * <p>인구감소지역 추천에서 "언제 가면 좋은가" 는 핵심 질문인데 지금까지 답이 없었다. 기상청이 기온·습도·바람·일조·강수를
 * 합쳐 이미 산출한 값이라 우리가 다시 조립할 필요가 없다.
 *
 * <p><b>외부 호출은 하루 한 번꼴이면 충분하다.</b> 이 API 는 요청 하나에 전 기간 × 전국을 통째로 주므로(실측 9일 ×
 * 236곳), 캐시 키를 <b>발표일 하나</b>로 두고 그 안에서 날짜·시군구를 찾는다. 지역별·날짜별로 나눠 부르면 같은 응답을
 * 수백 번 받게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourClimateService {

    /** 발표일·"오늘" 판정 모두 KST 기준. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 캐시 TTL. 하루 단위로 갱신되는 값이라 몇 시간 사이에 뒤집히지 않는다.
     *
     * <p>짧게 잡을수록 이득 없이 쿼터와 사용자 대기만 늘어난다 — 응답이 347KB·2.5초라 콜드 미스 비용이 작지 않다.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    /** 조회 실패·빈 응답의 짧은 재시도 TTL — 실패를 성공만큼 오래 붙들지 않는다. */
    private static final Duration RETRY_TTL = Duration.ofMinutes(10);

    /**
     * 키가 발표일이라 하루에 하나씩만 는다. 날이 바뀌며 밀려나는 만큼만 잡는다.
     */
    private static final int MAX_CACHED_DAYS = 3;

    /** loader 가 단일 호출이지만 응답이 커 실측 2.5초다. timeout(15초)에 여유를 얹었다. */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(16);

    private final TourClimateIndexClient tourClimateIndexClient;

    /** 캐시를 켜고 끄는 스위치(#403). 조회마다 물어, 운영 중 바뀐 값도 곧바로 듣는다. */
    private final ExternalApiCachePolicy cachePolicy;

    private final ExternalDataCache<LocalDate, Map<LocalDate, Map<SigunguKey, TourClimateIndex>>> cache =
            new ExternalDataCache<>(MAX_CACHED_DAYS, FIRST_LOAD_WAIT, this::cacheEnabled);

    /**
     * 그 지역·그 날짜의 관광기후지수. 범위 밖·조회 실패·매칭 실패면 빈 Optional.
     *
     * @param sido 정식({@code 충청북도})·축약({@code 충북}) 어느 표기든 받는다
     * @param sigungu 시군구명
     */
    public Optional<TourClimateIndex> indexOf(String sido, String sigungu, LocalDate date) {
        if (date == null || !TourClimateIndex.covers(LocalDate.now(SERVICE_ZONE), date)) {
            return Optional.empty();
        }
        SigunguKey key = SigunguKey.of(sido, sigungu);
        if (key == null) {
            return Optional.empty();
        }
        TourClimateIndex index = forecast().getOrDefault(date, Map.of()).get(key);
        if (index == null) {
            // 조용히 넘기지 않는다 — 기상청이 시군구명 표기를 바꾸면 여기가 늘어난다.
            log.warn("관광기후지수에서 시군구를 찾지 못했습니다 sido={} sigungu={} date={}", sido, sigungu, date);
            return Optional.empty();
        }
        return Optional.of(index);
    }

    /** 전 기간 × 전국 지수 — 워밍이 미리 데우는 단위이자 캐시의 단위다. */
    public Map<LocalDate, Map<SigunguKey, TourClimateIndex>> forecast() {
        return cache.get(
                LocalDate.now(SERVICE_ZONE),
                (key, stale) -> {
                    Map<LocalDate, Map<SigunguKey, TourClimateIndex>> fresh = tourClimateIndexClient.forecast();
                    if (fresh.isEmpty()) {
                        // 빈 응답을 성공 TTL 로 굳히지 않는다 — 실패와 결과가 같으므로 짧게 잡아 재시도를 유도한다.
                        // 직전 정상값이 있으면 유지한다(stale-while-error). 지수는 하루 단위라 어제 값이라도
                        // 버리는 것보다 낫고, 버리면 추천 가중치가 그 사이 통째로 빠진다.
                        log.warn("관광기후지수가 비어 왔습니다 — 직전 값 유지 baseDate={}", key);
                        return new Loaded<>(stale != null ? stale : fresh, RETRY_TTL);
                    }
                    return new Loaded<>(fresh, CACHE_TTL);
                },
                Map.of());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }

    /**
     * 캐시를 지금 써도 되나(#403).
     *
     * <p>람다로 필드를 직접 읽지 않고 메서드 참조를 쓰는 이유 — 캐시 필드의 초기화식은 생성자가
     * {@code cachePolicy} 를 넣기 <b>전에</b> 돌아서, 거기서 blank final 을 읽으면 컴파일이 막힌다.
     * 메서드 본문은 그때 읽히지 않는다.
     */
    private boolean cacheEnabled() {
        return cachePolicy.cacheEnabled(ExternalApi.KMA_WEATHER);
    }
}

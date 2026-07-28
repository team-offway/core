package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tago.TrainInfoClient;
import com.offway.core.transport.infrastructure.tago.dto.Station;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지역·출발지를 기차역으로 해석한다 — 열차 접근 정보를 붙이기 위한 관문.
 *
 * <p>TAGO 역 목록은 <b>시/도(광역) 단위</b>라 시군구로 바로 못 찾고, <b>좌표가 없어</b> 최근접도 못 쓴다. 그래서:
 * <ul>
 *   <li><b>지역(목적지)</b> → 시도로 광역 코드를 얻고, 그 광역 역 목록에서 <b>시군구명 이름 매칭</b>(정선군→정선역). 오지라 동명
 *       역이 없으면 빈 결과(= "이 지역은 열차로 갈 수 없음", 유효한 결과).
 *   <li><b>출발지</b> → 주요 거점역을 <b>큐레이션</b>해 출발 좌표에서 가장 가까운 역(임계 반경 내). 대부분 여행은 대도시 출발.
 * </ul>
 *
 * <p>광역 역 목록은 거의 안 변해 인메모리로 길게 캐시한다({@link ExternalDataCache}).
 */
@Service
@RequiredArgsConstructor
public class TrainStationResolver {

    /** 광역 역 목록 캐시 TTL — 역은 거의 불변. */
    private static final Duration STATIONS_TTL = Duration.ofHours(24);
    private static final Duration EMPTY_TTL = Duration.ofMinutes(10);
    /** 출발지↔거점역 최대 허용 거리(㎞) — 이보다 멀면 "가까운 출발역 없음". */
    private static final double ORIGIN_MAX_KM = 60.0;

    /** 지역 시도명(정식/변형) → TAGO 광역 코드. 인천(강화·옹진 섬)은 TAGO 열차 광역이 없어 제외. */
    private static final Map<String, String> SIDO_TO_CITY = Map.ofEntries(
            Map.entry("서울특별시", "11"), Map.entry("세종특별자치시", "12"),
            Map.entry("부산광역시", "21"), Map.entry("대구광역시", "22"),
            Map.entry("광주광역시", "24"), Map.entry("대전광역시", "25"), Map.entry("울산광역시", "26"),
            Map.entry("경기도", "31"),
            Map.entry("강원도", "32"), Map.entry("강원특별자치도", "32"),
            Map.entry("충청북도", "33"), Map.entry("충청남도", "34"),
            Map.entry("전라북도", "35"), Map.entry("전북특별자치도", "35"),
            Map.entry("전라남도", "36"), Map.entry("경상북도", "37"), Map.entry("경상남도", "38"));

    /** 주요 출발 거점역(코드·좌표) — 출발 좌표 최근접 해석용. 대도시·환승 거점. */
    private static final List<OriginHub> ORIGIN_HUBS = List.of(
            new OriginHub(new Station("NAT010000", "서울"), new Coordinate(37.5547, 126.9707)),
            new OriginHub(new Station("NAT010415", "수원"), new Coordinate(37.2657, 127.0009)),
            new OriginHub(new Station("NAT011668", "대전"), new Coordinate(36.3320, 127.4340)),
            new OriginHub(new Station("NAT013271", "동대구"), new Coordinate(35.8797, 128.6285)),
            new OriginHub(new Station("NAT014445", "부산"), new Coordinate(35.1151, 129.0413)),
            new OriginHub(new Station("NAT031857", "광주송정"), new Coordinate(35.1394, 126.7913)));

    private final TrainInfoClient trainInfoClient;
    private final ExternalDataCache<String, List<Station>> stationCache = new ExternalDataCache<>();

    /** 지역(시도·시군구)의 대표 기차역. 광역 코드가 없거나 동명 역이 없으면 빈 Optional(=열차역 없음). */
    public Optional<Station> forRegion(String sido, String sigungu) {
        String cityCode = SIDO_TO_CITY.get(sido);
        if (cityCode == null || sigungu == null) {
            return Optional.empty();
        }
        String base = stripSuffix(sigungu);
        List<Station> stations = stationsInCity(cityCode);
        // 정확 일치 우선, 없으면 이름 포함(예: 삼척 → 삼척/삼척해변 중 정확한 "삼척").
        return stations.stream().filter(s -> base.equals(s.name())).findFirst()
                .or(() -> stations.stream().filter(s -> s.name().contains(base)).findFirst());
    }

    /** 출발 좌표에서 임계 반경 내 가장 가까운 거점 출발역. 없으면 빈 Optional. */
    public Optional<Station> forOrigin(double lat, double lng) {
        Coordinate origin = new Coordinate(lat, lng);
        return ORIGIN_HUBS.stream()
                .filter(hub -> hub.coord().haversineKmTo(origin) <= ORIGIN_MAX_KM)
                .min((a, b) -> Double.compare(a.coord().haversineKmTo(origin), b.coord().haversineKmTo(origin)))
                .map(OriginHub::station);
    }

    private List<Station> stationsInCity(String cityCode) {
        return stationCache.get(cityCode, (code, stale) -> {
            List<Station> fresh = trainInfoClient.stationsInCity(code);
            return new Loaded<>(fresh, fresh.isEmpty() ? EMPTY_TTL : STATIONS_TTL);
        }, List.of());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        stationCache.evictAll();
    }

    /** 시군구명에서 행정 접미사를 떼 역명 매칭 기반을 만든다(정선군→정선, 태백시→태백). */
    private static String stripSuffix(String sigungu) {
        return sigungu.replaceAll("(특별자치시|특별자치도|광역시|특별시|시|군|구)$", "");
    }

    private record OriginHub(Station station, Coordinate coord) {}
}

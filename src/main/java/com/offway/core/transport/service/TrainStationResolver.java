package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.Station;
import com.offway.core.transport.domain.TrainStation;
import com.offway.core.transport.repository.TrainStationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 좌표를 기차역으로 해석한다 — 열차 접근의 관문. 전국 역 마스터(시드 343역)를 인메모리로 들고 <b>좌표 최근접</b>으로 찾는다.
 *
 * <p>이전 방식(광역 역목록 라이브 조회 + 시군구명 이름매칭)의 한계를 없앤다: 역 없는 오지도 <b>근교 최근접 역</b>(예: 경주역)을
 * 잡고, 출발지도 큐레이션 없이 실좌표로 해석한다. 역이 없는 진짜 오지({@value #MAX_KM}㎞ 밖)는 빈 Optional = "열차로 못 감".
 */
@Service
@RequiredArgsConstructor
public class TrainStationResolver {

    /** 이 반경 안에 역이 없으면 "열차 접근 불가"로 본다(출발지·지역 공통). */
    private static final double MAX_KM = 50.0;

    private final TrainStationRepository stationRepository;
    private volatile List<TrainStation> cache;

    /** 좌표에서 {@value #MAX_KM}㎞ 안 가장 가까운 역. 없으면 빈 Optional. */
    public Optional<Station> nearest(double lat, double lng) {
        Coordinate target = new Coordinate(lat, lng);
        return stations().stream()
                .filter(TrainStation::hasCoordinate)
                .map(s -> Map.entry(s, target.haversineKmTo(new Coordinate(s.getLat(), s.getLng()))))
                .filter(entry -> entry.getValue() <= MAX_KM)
                .min(Comparator.comparingDouble(Map.Entry::getValue))
                .map(entry -> new Station(entry.getKey().getCode(), entry.getKey().getName()));
    }

    /** 캐시 무효화 — 시드 갱신·통합 테스트 격리용. */
    public void evictCache() {
        cache = null;
    }

    private List<TrainStation> stations() {
        List<TrainStation> local = cache;
        if (local == null) {
            local = stationRepository.findAll();
            cache = local;
        }
        return local;
    }
}

package com.offway.core.transport.service;

import com.offway.core.common.geo.Coordinate;
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

    /**
     * 최근접 역보다 이만큼까지 더 먼 역만 대안으로 본다(#435).
     *
     * <p><b>절대 반경만으로는 부족하다.</b> 서울에서는 3순위 역까지 4㎞ 안이라 문제가 안 보이는데,
     * 강릉에서는 강릉(2.2㎞) 다음이 정동진(15.8㎞)·망상해변(26.7㎞)이다. 반경 50㎞만 보면 "열차를
     * 타려면 26㎞ 떨어진 망상해변으로 가세요" 가 되는데, 그건 안내가 아니라 다른 여행이다.
     *
     * <p><b>왜 상대값인가.</b> 이미 35㎞를 가야 역이 있는 완도 같은 곳에서는 7㎞ 더 가는 것이 대수롭지
     * 않고, 역이 2㎞ 앞에 있는 강릉에서는 26㎞가 터무니없다. 같은 절대 거리가 곳에 따라 다른 뜻이라
     * <b>최근접 역을 기준으로</b> 잰다.
     *
     * <p>10㎞ 는 실측으로 골랐다 — 수도권·광역시는 3순위까지 전부 이 안에 들어오고(서울 +4.0 · 부산
     * +4.5 · 대구 +4.2 · 광주 +5.0 · 대전 +7.2), 강릉(+24.4)·안동(+22.1)은 걸러진다.
     */
    private static final double NEARBY_MARGIN_KM = 10.0;

    private final TrainStationRepository stationRepository;
    private volatile List<TrainStation> cache;

    /** 좌표에서 {@value #MAX_KM}㎞ 안 가장 가까운 역. 없으면 빈 Optional. */
    public Optional<Station> nearest(double lat, double lng) {
        List<Station> candidates = nearestCandidates(lat, lng, 1);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.getFirst());
    }

    /**
     * 좌표에서 {@value #MAX_KM}㎞ 안 가까운 순으로 최대 {@code limit} 곳(#435).
     *
     * <p><b>왜 하나로 끝내지 않는가.</b> 최근접 역이 그 목적지 방면 노선이 아니면 열차가 통째로 사라진다.
     * 강변역에서 제천에 갈 때 수서(5.80㎞)가 왕십리(6.02㎞)를 <b>0.22㎞ 차이로</b> 이기는데, 수서는 SRT
     * 전용이라 제천행이 없고 왕십리는 중앙선이라 있다. 거리만으로 하나를 고르면 그 0.22㎞ 때문에
     * 사용자는 탈 수 있는 열차를 못 본다.
     *
     * <p>고르는 일은 여기서 하지 않는다. 이 서비스는 "가까운 순으로 이만큼" 만 답하고, 어느 역에 실제로
     * 운행이 있는지는 조회해 봐야 아는 {@link TrainAccessService} 의 몫이다.
     *
     * <p><b>대안은 {@value #NEARBY_MARGIN_KM}㎞ 안에서만 찾는다.</b> 출발지는 전국 어디든 될 수 있고,
     * 시골에서는 다음 역이 20㎞ 넘게 떨어져 있다 — 그런 역을 권하면 안내가 아니라 다른 여행이 된다.
     * 그래서 후보가 {@code limit} 보다 적게 나오는 것이 정상이다.
     */
    public List<Station> nearestCandidates(double lat, double lng, int limit) {
        Coordinate target = new Coordinate(lat, lng);
        List<Map.Entry<TrainStation, Double>> byDistance = stations().stream()
                .filter(TrainStation::hasCoordinate)
                .map(s -> Map.entry(s, target.haversineKmTo(new Coordinate(s.getLat(), s.getLng()))))
                .filter(entry -> entry.getValue() <= MAX_KM)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .toList();
        if (byDistance.isEmpty()) {
            return List.of();
        }
        double cutoff = byDistance.getFirst().getValue() + NEARBY_MARGIN_KM;
        return byDistance.stream()
                .filter(entry -> entry.getValue() <= cutoff)
                .limit(limit)
                .map(entry -> new Station(
                        entry.getKey().getCode(),
                        entry.getKey().getName(),
                        new Coordinate(entry.getKey().getLat(), entry.getKey().getLng())))
                .toList();
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

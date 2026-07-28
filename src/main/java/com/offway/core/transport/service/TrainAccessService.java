package com.offway.core.transport.service;

import com.offway.core.transport.domain.Station;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.service.dto.TrainAccess;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지역까지의 열차 접근 조회 — transport 가 itinerary(코스)에 노출하는 공개 서비스. 출발 좌표·목적지 지역·날짜로 "열차로 어떻게
 * 가나"를 {@link TrainAccess}(있음/역없음/그날없음/조회불가)로 준다.
 *
 * <p>역 해석({@link TrainStationResolver})과 열차 조회({@link TrainRouteService})를 조립할 뿐, 캐시·폴백은 각 하위
 * 서비스가 담당한다. 출발지·목적지 좌표에서 각각 최근접 역을 찾고, 어느 쪽이든 근교에 역이 없으면 {@code NO_STATION} — 오지
 * 인구감소지역엔 흔한 정상 결과다.
 */
@Service
@RequiredArgsConstructor
public class TrainAccessService {

    private final TrainStationResolver stationResolver;
    private final TrainRouteService trainRouteService;

    /** 출발 좌표에서 목적지 좌표(지역)까지, 해당 날짜의 열차 접근. 각 좌표의 최근접 역으로 조회한다. */
    public TrainAccess accessTo(double originLat, double originLng, double destLat, double destLng, LocalDate date) {
        Optional<Station> from = stationResolver.nearest(originLat, originLng);
        Optional<Station> to = stationResolver.nearest(destLat, destLng);
        if (from.isEmpty() || to.isEmpty()) {
            return TrainAccess.noStation(from.map(Station::name).orElse(null), to.map(Station::name).orElse(null));
        }
        String fromName = from.get().name();
        String toName = to.get().name();
        TrainAvailability availability = trainRouteService.fastestTrain(from.get().id(), to.get().id(), date);
        return switch (availability) {
            case TrainAvailability.Available a -> TrainAccess.available(fromName, toName, a.fastest());
            case TrainAvailability.NoServiceOnDate n -> TrainAccess.noServiceOnDate(fromName, toName);
            case TrainAvailability.Unavailable u -> TrainAccess.unavailable(fromName, toName);
        };
    }
}

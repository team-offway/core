package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.Station;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.service.dto.TrainAccess;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역까지의 열차 접근 조회 — transport 가 itinerary(코스)에 노출하는 공개 서비스. 출발 좌표·목적지 지역·날짜로 "열차로 어떻게
 * 가나"를 {@link TrainAccess}(있음/역없음/그날없음/조회불가)로 준다.
 *
 * <p>역 해석({@link TrainStationResolver})과 열차 조회({@link TrainRouteService})를 조립할 뿐, 캐시·폴백은 각 하위
 * 서비스가 담당한다. 출발지·목적지 좌표에서 각각 최근접 역을 찾고, 어느 쪽이든 근교에 역이 없으면 {@code NO_STATION} — 오지
 * 인구감소지역엔 흔한 정상 결과다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainAccessService {

    private final TrainStationResolver stationResolver;
    private final TrainRouteService trainRouteService;

    /**
     * 출발 좌표에서 목적지 좌표(지역)까지, 해당 날짜의 열차 접근. 각 좌표의 최근접 역으로 조회한다.
     *
     * <p><b>degrade 는 사유를 남긴다.</b> 열차 정보가 없어도 코스는 그대로 나가므로, 여기서 남기지 않으면 외부 장애와
     * "원래 열차가 없는 오지" 가 화면에서 똑같이 보인다. 레벨로 그 둘을 가른다.
     */
    public TrainAccess accessTo(double originLat, double originLng, double destLat, double destLng, LocalDate date) {
        Optional<Station> from = stationResolver.nearest(originLat, originLng);
        Optional<Station> to = stationResolver.nearest(destLat, destLng);
        if (from.isEmpty() || to.isEmpty()) {
            // 오지 인구감소지역엔 흔한 정상 결과라 warn 이 아니다. 다만 어느 쪽이 없었는지는 남긴다.
            log.info("열차 접근 불가 — 근교 역 없음 출발역={} 도착역={}",
                    from.map(Station::name).orElse("없음"), to.map(Station::name).orElse("없음"));
            return TrainAccess.noStation(from.map(Station::name).orElse(null), to.map(Station::name).orElse(null));
        }
        String fromName = from.get().name();
        String toName = to.get().name();
        // 도착역 좌표는 조회 결과와 무관하게 넘긴다 — 그 지역에 열차로 간다면 내리는 곳은 어차피 이 역이다(#127).
        Coordinate toPoint = to.get().coordinate();
        TrainAvailability availability = trainRouteService.fastestTrain(from.get().id(), to.get().id(), date);
        return switch (availability) {
            case TrainAvailability.Available a -> TrainAccess.available(fromName, toName, toPoint, a.fastest());
            case TrainAvailability.NoServiceOnDate n -> {
                // 조회는 성공했고 그날 운행이 없을 뿐 — 정상 흐름이다.
                log.info("열차 미운행 — {}→{} date={}", fromName, toName, date);
                yield TrainAccess.noServiceOnDate(fromName, toName, toPoint);
            }
            case TrainAvailability.Unavailable u -> {
                // 외부 실패다. 이것만 warn 이라야 "역이 없다" 와 구분해 장애를 찾을 수 있다.
                log.warn("열차 조회 실패 — 접근 정보 없이 코스를 내립니다 {}→{} date={}", fromName, toName, date);
                yield TrainAccess.unavailable(fromName, toName, toPoint);
            }
        };
    }
}

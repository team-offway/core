package com.offway.core.transport.service;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.Station;
import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.service.dto.RegionAccess;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역까지의 열차 접근 조회 — transport 가 itinerary(코스)에 노출하는 공개 서비스. 출발 좌표·목적지 지역·날짜로 "열차로 어떻게
 * 가나"를 {@link RegionAccess}(있음/역없음/그날없음/조회불가)로 준다.
 *
 * <p>역 해석({@link TrainStationResolver})과 열차 조회({@link TrainRouteService})를 조립할 뿐, 캐시·폴백은 각 하위
 * 서비스가 담당한다. 출발지·목적지 좌표에서 각각 최근접 역을 찾고, 어느 쪽이든 근교에 역이 없으면 {@code NO_STATION} — 오지
 * 인구감소지역엔 흔한 정상 결과다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainAccessService {

    /**
     * 출발 쪽에서 몇 곳까지 물어볼 것인가(#435).
     *
     * <p><b>왜 하나로는 안 되나.</b> 강변역에서 제천에 갈 때 최근접은 수서(5.80㎞)인데 SRT 전용이라
     * 제천행이 없다. 제천행이 서는 왕십리는 6.02㎞ — <b>0.22㎞ 차이로</b> 밀려서, 탈 수 있는 열차가
     * 통째로 사라진다.
     *
     * <p><b>왜 셋인가.</b> 그 사례는 둘이면 풀리고, 셋이면 청량리(6.71㎞)까지 여유가 생긴다. 반대로
     * 늘릴수록 외부 조회가 그만큼 곱해진다 — 최근접 역에 운행이 있으면 한 번에 끝나므로 실제 평균은
     * 1에 가깝지만, 최악은 이 값만큼이다.
     *
     * <p>도착 쪽은 하나로 둔다. 도착역은 <b>지역 안 동선의 기준점</b>이라(#127) 바꾸면 코스 전체가
     * 다시 짜인다 — 시간표를 얻자고 코스 지리를 흔들 수는 없다.
     */
    private static final int ORIGIN_CANDIDATES = 3;

    private final TrainStationResolver stationResolver;
    private final TrainRouteService trainRouteService;

    /**
     * 출발 좌표에서 목적지 좌표(지역)까지, 해당 날짜의 열차 접근. 각 좌표의 최근접 역으로 조회한다.
     *
     * <p><b>degrade 는 사유를 남긴다.</b> 열차 정보가 없어도 코스는 그대로 나가므로, 여기서 남기지 않으면 외부 장애와
     * "원래 열차가 없는 오지" 가 화면에서 똑같이 보인다. 레벨로 그 둘을 가른다.
     *
     * @param notBefore 집을 나서는 시각. 이 시각 이후에 떠나는 편만 고른다 — 반차를 내고 12시에 나서는 사용자에게
     *     새벽 첫차를 잡아주면 첫날 일정이 지킬 수 없는 약속이 된다(#138)
     */
    public RegionAccess accessTo(
            double originLat, double originLng, double destLat, double destLng, LocalDate date, LocalTime notBefore) {
        List<Station> origins = stationResolver.nearestCandidates(originLat, originLng, ORIGIN_CANDIDATES);
        Optional<Station> to = stationResolver.nearest(destLat, destLng);
        if (origins.isEmpty() || to.isEmpty()) {
            // 오지 인구감소지역엔 흔한 정상 결과라 warn 이 아니다. 다만 어느 쪽이 없었는지는 남긴다.
            log.debug("열차 접근 불가 — 근교 역 없음 출발역={} 도착역={}",
                    origins.isEmpty() ? "없음" : origins.getFirst().name(),
                    to.map(Station::name).orElse("없음"));
            return RegionAccess.noStation(
                    origins.isEmpty() ? null : origins.getFirst().name(), to.map(Station::name).orElse(null));
        }
        String toName = to.get().name();
        // 도착역 좌표는 조회 결과와 무관하게 넘긴다 — 그 지역에 열차로 간다면 내리는 곳은 어차피 이 역이다(#127).
        Coordinate toPoint = to.get().coordinate();

        RegionAccess firstAttempt = null;
        for (Station from : origins) {
            RegionAccess attempt = accessFrom(from, to.get(), toName, toPoint, date, notBefore);
            if (attempt.status() == RegionAccess.Status.AVAILABLE) {
                if (firstAttempt != null) {
                    // 최근접 역을 건너뛰고 더 먼 역을 골랐다. 화면의 출발역이 "가장 가까운 역" 과 달라지므로
                    // 왜 그랬는지 남긴다 — 이게 없으면 나중에 오탐으로 오해한다.
                    log.debug("최근접 역에 운행이 없어 다음 후보로 넘어갔다 — {}({}) → {}",
                            origins.getFirst().name(), firstAttempt.status(), from.name());
                }
                return attempt;
            }
            if (firstAttempt == null) {
                firstAttempt = attempt; // 실패로 끝나면 이 사유를 돌려준다 — 최근접 역이 답한 것이 가장 정확하다
            }
            if (attempt.status() == RegionAccess.Status.UNAVAILABLE) {
                // 외부 장애다. 다른 역을 물어도 같은 실패에 시간만 더 쓴다 — 조회 하나가 최대 6초다.
                break;
            }
        }
        return firstAttempt;
    }

    /**
     * 출발역 하나로 조회한 결과(#435).
     *
     * <p>후보를 돌며 부르므로 <b>순수한 매핑</b>으로 둔다 — 어느 후보를 쓸지 고르는 판단은 호출자가 한다.
     */
    private RegionAccess accessFrom(
            Station from, Station to, String toName, Coordinate toPoint, LocalDate date, LocalTime notBefore) {
        String fromName = from.name();
        TrainAvailability availability = trainRouteService.fastestTrain(from.id(), to.id(), date);
        return switch (availability) {
            case TrainAvailability.Available a -> a.earliestArrivalDepartingFrom(notBefore)
                    // 시간표는 <b>공짜다</b>(#414). 하루치를 이미 받아 캐시에 들고 있어서(#138) 여기서
                    // 화면에 올릴 편을 고르는 데 외부 호출이 한 건도 늘지 않는다.
                    .map(leg -> RegionAccess.available(fromName, toName, toPoint, leg, upcoming(a, notBefore)))
                    .orElseGet(() -> {
                        // 그날 운행은 있는데 이 시각 이후 편이 없다 — 막차가 지났다. 사용자에게는 "그날 열차가
                        // 없음" 과 같은 결과라 같은 상태로 답한다. 다만 이유가 달라 로그로 가른다.
                        log.debug("출발 시각 이후 열차 없음 — {}→{} date={} notBefore={}", fromName, toName, date, notBefore);
                        return RegionAccess.noServiceOnDate(fromName, toName, toPoint);
                    });
            case TrainAvailability.NoServiceOnDate n -> {
                // 조회는 성공했고 그날 운행이 없을 뿐 — 정상 흐름이다.
                log.debug("열차 미운행 — {}→{} date={}", fromName, toName, date);
                yield RegionAccess.noServiceOnDate(fromName, toName, toPoint);
            }
            case TrainAvailability.Unavailable u -> {
                // 외부 실패다. 이것만 warn 이라야 "역이 없다" 와 구분해 장애를 찾을 수 있다.
                log.warn("열차 조회 실패 — 접근 정보 없이 코스를 내립니다 {}→{} date={}", fromName, toName, date);
                yield RegionAccess.unavailable(fromName, toName, toPoint);
            }
        };
    }

    /**
     * 화면에 올릴 편 — 집을 나서는 시각 이후, 이른 순으로(#414).
     *
     * <p>{@code earliestArrivalDepartingFrom} 과 <b>정렬이 다르다</b>. 그쪽은 코스를 짜려고 가장 일찍 닿는 편을
     * 고르지만, 시간표는 "다음 차가 몇 시인가" 라 출발 순이 맞다.
     */
    private static List<Departure> upcoming(TrainAvailability.Available available, LocalTime notBefore) {
        return Departure.upcoming(available.legs().stream().map(TrainLeg::toDeparture).toList(), notBefore);
    }
}

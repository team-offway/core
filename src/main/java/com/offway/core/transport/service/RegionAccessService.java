package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.RegionArrival;
import com.offway.core.transport.service.dto.RegionAccess;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역까지의 대중교통 접근 조회(#97) — transport 가 itinerary(코스)에 노출하는 공개 서비스. 열차 하나만 보던 것을
 * 버스·여객선까지 넓힌 자리다.
 *
 * <p><b>왜 필요했나.</b> 코스는 "내린 곳" 을 지역 안 동선의 기준점으로 쓴다(#127). 그런데 그 지점을 열차역에서만
 * 찾아, 역이 없는 지역은 <b>출발지 좌표</b>로 되돌아갔다 — 서울에서 출발하면 "완도 장소들 중 서울에서 가까운 곳"
 * 부터 이어붙는 동선이 나온다. 버스 터미널 789곳·항구 500곳이 이미 시드돼 있는데도(#107 · #97) 코스가 쓰지 않던
 * 상태였다.
 *
 * <p><b>외부 호출이 늘지 않는다.</b> 터미널·항구 해석은 DB 시드를 인메모리로 들고 하는 좌표 최근접이라 네트워크를
 * 타지 않는다. 늘어난 것은 열차가 실패했을 때의 메모리 탐색 두 번뿐이다.
 *
 * <p>고르는 규칙 자체는 {@link RegionAccess#orNearer} 가 소유한다 — 서비스는 후보를 모아 넘길 뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionAccessService {

    private final TrainAccessService trainAccessService;
    private final BusTerminalResolver busTerminalResolver;
    private final FerryPortResolver ferryPortResolver;

    /**
     * 출발 좌표에서 목적지 좌표(지역)까지, 해당 날짜의 대중교통 접근.
     *
     * @param notBefore 집을 나서는 시각 — 이 시각 이후에 떠나는 편만 고른다(#138)
     */
    public RegionAccess accessTo(
            double originLat, double originLng, double destLat, double destLng, LocalDate date, LocalTime notBefore) {
        RegionAccess train = trainAccessService.accessTo(originLat, originLng, destLat, destLng, date, notBefore);
        RegionAccess chosen = train.orNearer(
                new Coordinate(destLat, destLng), busArrival(destLat, destLng), ferryArrival(destLat, destLng));
        if (chosen != train) {
            // 열차만 보던 시절 이 지역은 도착 지점을 몰라 출발지로 되돌아갔다. 무엇이 그 자리를 채웠는지 남긴다.
            log.debug("도착 지점을 {}(으)로 잡습니다 — 열차 상태={} 지점={}",
                    chosen.mode().label(), train.status(), chosen.toName());
        }
        return chosen;
    }

    private RegionArrival busArrival(double lat, double lng) {
        return busTerminalResolver.nearest(lat, lng).map(RegionArrival::of).orElse(null);
    }

    private RegionArrival ferryArrival(double lat, double lng) {
        return ferryPortResolver.nearest(lat, lng).map(RegionArrival::of).orElse(null);
    }
}

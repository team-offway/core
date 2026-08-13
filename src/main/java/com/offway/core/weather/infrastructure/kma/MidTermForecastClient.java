package com.offway.core.weather.infrastructure.kma;

import com.offway.core.weather.domain.MidLandRegion;
import com.offway.core.weather.domain.MidTermOutlook;
import java.util.Optional;

/**
 * 기상청 중기 육상예보 port(#129) — 단기예보가 못 닿는 D+4 에서 D+10 을 맡는다.
 *
 * <p>도메인은 이 인터페이스에만 의존하고 구현은 {@code MidTermForecastClientImpl} 에 격리한다. 키가 없으면 빈 Optional 로
 * 떨어져 로컬 실행성이 깨지지 않는다.
 */
public interface MidTermForecastClient {

    /** 구역의 중기 전망. 키 없음·조회 실패면 빈 Optional(날씨는 부가 정보라 코스를 막지 않는다). */
    Optional<MidTermOutlook> outlook(MidLandRegion region);
}

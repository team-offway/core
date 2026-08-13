package com.offway.core.weather.infrastructure.kma;

import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 기상청 단기예보(동네예보) 조회 port. 위경도·날짜로 그 날의 날씨 요약을 준다.
 *
 * <p>키가 없거나 호출/파싱 실패, 또는 예보 범위(발표일 기준 ~3일) 밖이면 빈 Optional — 날씨는 부가 정보라 실패로 코스를 막지 않는다.
 */
public interface KmaWeatherClient {

    /** 좌표 지점의 해당 날짜 날씨 요약. 없으면 빈 Optional. */
    Optional<DailyWeather> dailyForecast(double lat, double lng, LocalDate date);

    /**
     * 어댑터가 들고 있는 예보 캐시를 비운다 — 운영상 강제 갱신, 그리고 통합 테스트 격리용.
     *
     * <p>어댑터가 캐시를 갖는 것은 이 API 만의 사정이라(응답 하나가 5일치를 담는다) 포트에 기본 no-op 을 둔다.
     * 캐시가 없는 구현(테스트 stub 등)은 아무것도 하지 않아도 계약이 성립한다.
     */
    default void evictCache() {
        // 기본은 no-op — 캐시를 들지 않는 구현을 위해.
    }
}

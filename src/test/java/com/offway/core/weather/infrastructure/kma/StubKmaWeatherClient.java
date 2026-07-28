package com.offway.core.weather.infrastructure.kma;

import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link KmaWeatherClient} 외부 경계 stub — 통합 테스트에서 기상청 단기예보 조회를 격리한다.
 *
 * <p>다른 외부 stub 과 달리 default 는 throw 가 아니라 <b>빈 Optional</b> 이다. 단기예보는 발표일 기준 ~3일만 커버해
 * "예보 범위 밖 → 없음" 이 정상 도메인 결과이고, 날씨는 코스의 부가 정보라 대부분의 코스 테스트에서 부재가 자연스럽다. 날씨 자체를
 * 검증하는 테스트만 {@code respond(...)} 로 예보를 지정한다.
 */
public class StubKmaWeatherClient implements KmaWeatherClient {

    private Supplier<Optional<DailyWeather>> behavior = Optional::empty;

    /** 모든 좌표·날짜 조회에 같은 예보를 돌려준다. */
    public void respond(Supplier<Optional<DailyWeather>> behavior) {
        this.behavior = behavior;
    }

    @Override
    public Optional<DailyWeather> dailyForecast(double lat, double lng, LocalDate date) {
        return behavior.get();
    }
}

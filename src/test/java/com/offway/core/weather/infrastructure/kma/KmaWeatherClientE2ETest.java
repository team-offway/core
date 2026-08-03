package com.offway.core.weather.infrastructure.kma;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 기상청 단기예보 실제 외부 호출 E2E — data.go.kr 을 stub 없이 직접 부른다. CI 기본 실행에서 제외한다({@code
 * DATA_GO_KR_SERVICE_KEY} 있을 때만).
 *
 * <p><b>이 테스트가 없으면 경로 폐기를 못 잡는다.</b> 통합 테스트는 {@code KmaWeatherClient} port 를 stub 으로 바꾸므로
 * 어댑터의 URL 을 아예 타지 않는다. 실제로 구버전 base 가 폐기된 뒤에도 통합 테스트는 전부 통과했고, 날씨만 조용히 비어 있었다
 * (#128). 접점 확인은 실호출로만 된다.
 *
 * <p>확인 대상은 base({@code VilageFcstInfoService_2.0})·오퍼레이션·파라미터명({@code base_date}/{@code base_time}/
 * {@code nx}/{@code ny})·응답 필드명({@code category}·{@code fcstDate}·{@code fcstValue})이다.
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class KmaWeatherClientE2ETest {

    /** 발표일·발표시각이 KST 기준이라 오늘 판정도 KST 로 한다. */
    private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul");

    /** 부산 동구 — 인구감소지역이자 격자 변환이 확인된 좌표. */
    private static final double LAT = 35.1284090;
    private static final double LNG = 129.0454604;

    private static KmaWeatherClient client() {
        ExternalApiProperties props = new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
        return new KmaWeatherClientImpl(WebClient.builder().build(), props);
    }

    @Test
    void 내일_예보를_실제로_받아온다() {
        // 오늘은 이미 지나간 새벽의 최저기온(TMN)이 안 와서 부분 응답이다. 내일부터가 완전한 하루다.
        LocalDate tomorrow = LocalDate.now(KMA_ZONE).plusDays(1);

        Optional<DailyWeather> weather = client().dailyForecast(LAT, LNG, tomorrow);

        DailyWeather actual = weather.orElseThrow(
                () -> new AssertionError("내일 예보가 비었습니다 — base 폐기·파라미터명·응답 스키마 변경을 의심하세요"));
        assertNotNull(actual.minTemp(), "TMN 이 안 왔습니다 — 카테고리명이 바뀌었을 수 있습니다");
        assertNotNull(actual.maxTemp(), "TMX 가 안 왔습니다");
        assertNotNull(actual.rainProbability(), "POP 이 안 왔습니다");
    }

    @Test
    void 단기예보는_사흘_뒤까지_완전하다() {
        // #129 가 중기예보를 D+4 부터 붙이는 근거다. 이 경계가 바뀌면 두 예보 사이에 구멍이 생긴다.
        LocalDate today = LocalDate.now(KMA_ZONE);
        KmaWeatherClient client = client();

        DailyWeather third = client.dailyForecast(LAT, LNG, today.plusDays(3))
                .orElseThrow(() -> new AssertionError("D+3 예보가 비었습니다 — 단기예보 범위가 줄었습니다"));

        assertNotNull(third.minTemp(), "D+3 은 최저기온까지 와야 한다");
        assertNotNull(third.maxTemp(), "D+3 은 최고기온까지 와야 한다");
    }

    @Test
    void 예보_범위_밖은_빈_값이다() {
        // 조용한 실패 방지의 반대편 — 없는 것을 지어내지 않는지 확인한다.
        LocalDate farFuture = LocalDate.now(KMA_ZONE).plusDays(30);

        assertTrue(client().dailyForecast(LAT, LNG, farFuture).isEmpty(),
                "예보 범위 밖인데 값이 왔습니다 — 날짜 필터가 동작하지 않습니다");
    }
}

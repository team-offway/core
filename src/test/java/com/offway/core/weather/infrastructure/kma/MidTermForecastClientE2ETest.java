package com.offway.core.weather.infrastructure.kma;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.MidLandRegion;
import com.offway.core.weather.domain.MidTermOutlook;
import com.offway.core.weather.domain.SkyState;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 기상청 중기 육상예보 실제 외부 호출 E2E(#129) — stub 없이 직접 부른다. CI 기본 실행에서 제외한다({@code
 * DATA_GO_KR_SERVICE_KEY} 있을 때만).
 *
 * <p>확인 대상은 base·오퍼레이션·구역 코드({@code regId})·발표시각 형식({@code tmFc})·응답 필드명
 * ({@code wf4Am}···{@code wf10})이다. 특히 <b>구역 코드는 전부 실제로 응답하는지</b> 확인한다 — 하나라도 죽어 있으면
 * 그 지역만 조용히 날씨가 사라진다.
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class MidTermForecastClientE2ETest {

    private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul");

    private static MidTermForecastClient client() {
        ExternalApiProperties props = new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
        return new MidTermForecastClientImpl(WebClient.builder().build(), props, new NoOpCallRecorder());
    }

    @ParameterizedTest(name = "{0} 구역이 실제로 응답한다")
    @EnumSource(MidLandRegion.class)
    void 열개_구역이_모두_응답한다(MidLandRegion region) {
        // 구역 하나가 죽으면 그 지역 사용자만 날씨가 비는데, 부가 정보라 아무도 모른다.
        MidTermOutlook outlook = client().outlook(region)
                .orElseThrow(() -> new AssertionError(region + "(" + region.regId() + ") 응답 없음"));

        assertTrue(outlook.byDate().size() >= 5,
                "D+4~D+10 중 최소 5일은 와야 한다. 실제=" + outlook.byDate().size());
    }

    @Test
    void 나흘_뒤부터_열흘_뒤까지_답한다() {
        // 단기예보가 D+3 까지라(#128 E2E 로 고정) 여기가 D+4 부터여야 두 예보 사이에 구멍이 없다.
        LocalDate today = LocalDate.now(KMA_ZONE);
        MidTermOutlook outlook = client().outlook(MidLandRegion.SEOUL_INCHEON_GYEONGGI).orElseThrow();

        assertTrue(outlook.on(today.plusDays(MidTermOutlook.FIRST_DAY)).isPresent(),
                "D+4 가 비었습니다 — 단기예보와 사이에 구멍이 생깁니다");
        assertTrue(outlook.on(today.plusDays(MidTermOutlook.LAST_DAY)).isPresent(),
                "D+10 이 비었습니다 — 중기예보 범위가 줄었습니다");
    }

    @Test
    void 하늘상태와_강수확률이_실제로_담긴다() {
        LocalDate target = LocalDate.now(KMA_ZONE).plusDays(MidTermOutlook.FIRST_DAY);

        var weather = client().outlook(MidLandRegion.SEOUL_INCHEON_GYEONGGI).orElseThrow().on(target).orElseThrow();

        assertNotNull(weather.rainProbability(), "강수확률이 안 왔습니다 — rnSt 필드명이 바뀌었을 수 있습니다");
        assertTrue(weather.sky() != SkyState.UNKNOWN,
                "하늘상태를 못 읽었습니다 — wf 문구가 아는 형태가 아닙니다");
        assertEquals(null, weather.minTemp(), "중기예보는 기온을 담지 않는다(지점 코드 미확보)");
    }

    @Test
    void 발표시각은_하루_두_번_중_직전_슬롯이다() {
        // 아직 안 나온 슬롯을 요청하면 빈 응답이 온다. 경계에서 어긋나면 하루 중 특정 시간대에만 날씨가 사라진다.
        assertEquals(LocalDateTime.of(2026, 8, 3, 18, 0),
                MidTermForecastClientImpl.latestRelease(LocalDateTime.of(2026, 8, 3, 23, 30)));
        assertEquals(LocalDateTime.of(2026, 8, 3, 6, 0),
                MidTermForecastClientImpl.latestRelease(LocalDateTime.of(2026, 8, 3, 17, 59)));
        assertEquals(LocalDateTime.of(2026, 8, 2, 18, 0),
                MidTermForecastClientImpl.latestRelease(LocalDateTime.of(2026, 8, 3, 5, 0)));
        // 발표 정각 직후는 아직 안 올라와 있다 — 지연을 빼고 직전 슬롯을 쓴다.
        assertEquals(LocalDateTime.of(2026, 8, 2, 18, 0),
                MidTermForecastClientImpl.latestRelease(LocalDateTime.of(2026, 8, 3, 6, 5)));
    }
}

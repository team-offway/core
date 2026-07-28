package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.service.TrainAccessService;
import com.offway.core.transport.service.TrainRouteService;
import com.offway.core.transport.service.TrainStationResolver;
import com.offway.core.transport.service.dto.TrainAccess;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 열차 접근 실호출 E2E — 역 이름매칭·출발지 해석·열차 조회 전체 경로를 stub 없이 data.go.kr 로 확인한다. 실 키 있을 때만
 * (같은 패키지라 package-private {@link TrainInfoClientImpl} 을 직접 생성).
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class TrainAccessE2ETest {

    private static final double SEOUL_LAT = 37.5547;
    private static final double SEOUL_LNG = 126.9707;

    private static TrainAccessService realService() {
        ExternalApiProperties props = new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
        TrainInfoClient client = new TrainInfoClientImpl(WebClient.builder().build(), props);
        return new TrainAccessService(new TrainStationResolver(client), new TrainRouteService(client));
    }

    @Test
    void 서울에서_정선은_역이_있어_열차조회까지_간다() {
        TrainAccess access =
                realService().accessTo(SEOUL_LAT, SEOUL_LNG, "강원특별자치도", "정선군", LocalDate.now().plusDays(1));

        // 정선은 역이 있으니 NO_STATION 이 아니어야 한다(운행 있음 또는 그날 미운행). 접점 검증.
        assertNotEquals(TrainAccess.Status.NO_STATION, access.status());
        assertEquals("정선", access.toStation());
    }

    @Test
    void 서울에서_철원은_역이_없어_NO_STATION() {
        TrainAccess access =
                realService().accessTo(SEOUL_LAT, SEOUL_LNG, "강원특별자치도", "철원군", LocalDate.now().plusDays(1));

        assertEquals(TrainAccess.Status.NO_STATION, access.status());
    }
}

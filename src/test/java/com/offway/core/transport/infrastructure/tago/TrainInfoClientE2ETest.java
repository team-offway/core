package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.infrastructure.tago.dto.TrainAvailability;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * TAGO 열차정보 실제 외부 호출 E2E — data.go.kr 을 stub 없이 직접 부른다. 네트워크 비결정성 탓에 CI 기본 실행에서 제외한다
 * ({@code DATA_GO_KR_SERVICE_KEY} 있을 때만). "실제 외부와 우리 코드의 접점"(경로·casing·응답 스키마)을 확인한다.
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class TrainInfoClientE2ETest {

    private static final String SEOUL = "NAT010000";
    private static final String BUSAN = "NAT014445";

    @Test
    void 서울에서_부산_가장_빠른_열차를_실제로_조회한다() {
        ExternalApiProperties props = new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
        TrainInfoClient client = new TrainInfoClientImpl(WebClient.builder().build(), props);

        TrainAvailability result = client.fastestTrain(SEOUL, BUSAN, LocalDate.now().plusDays(1));

        TrainAvailability.Available available =
                assertInstanceOf(TrainAvailability.Available.class, result, "서울→부산 열차가 조회돼야 한다");
        assertTrue(available.fastest().durationMinutes() > 0, "소요시간은 양수여야 한다");
        assertTrue(available.fastest().durationMinutes() < 600, "서울→부산은 10시간 미만이어야 한다");
    }
}

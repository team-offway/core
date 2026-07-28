package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.TrainAvailability;
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

        // 접점 검증 — 경로·casing·키가 정상이면 Unavailable 이 아니다(운행 있음, 또는 그 날짜 미운행). 특정 날짜에 열차가
        // 있는지는 KORAIL 데이터에 달려 flaky 하므로 접점만 단언한다.
        assertFalse(result instanceof TrainAvailability.Unavailable,
                "실호출 접점(경로·casing·키)이 정상이어야 한다 — Unavailable 은 조회 실패");
        if (result instanceof TrainAvailability.Available available) {
            assertTrue(available.fastest().durationMinutes() > 0, "소요시간은 양수여야 한다");
            assertTrue(available.fastest().durationMinutes() < 600, "서울→부산은 10시간 미만이어야 한다");
        }
    }
}

package com.offway.core.leave.infrastructure.holiday;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 특일정보 API 실호출 E2E — "외부 API 가 실제로 불러와지는가" 를 증명한다.
 *
 * <p>격리: 키 환경변수({@code DATA_GO_KR_SERVICE_KEY})가 있을 때만 실행된다. 없으면 스킵 — CI 기본 실행에서 실 외부 호출·flaky 를
 * 막는다. 로컬에서 키를 넣고 돌려 접점을 확인한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class HolidayClientE2ETest {

    @Autowired
    private HolidayClient holidayClient;

    @Test
    @DisplayName("2026년 1월 특일정보를 실제로 불러와 신정을 포함한다")
    void 실제_특일정보를_불러온다() {
        Set<LocalDate> holidays = holidayClient.getHolidays(2026, 1);

        assertFalse(holidays.isEmpty(), "실제 API 가 공휴일을 하나도 안 돌려줬다 — 키·승인·파라미터 확인");
        assertTrue(holidays.contains(LocalDate.of(2026, 1, 1)), "2026-01-01 신정이 결과에 없다: " + holidays);
    }
}

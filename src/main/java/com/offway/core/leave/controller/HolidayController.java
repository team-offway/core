package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.HolidayResponse;
import com.offway.core.leave.domain.HolidayYear;
import com.offway.core.leave.service.LeaveService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공휴일 조회(#317).
 *
 * <p>연차 도메인이 특일정보를 소유하므로 여기 둔다. 다만 경로는 {@code /leaves} 아래가 아니라 별도 자원이다 —
 * 공휴일은 특정 사용자의 연차가 아니라 <b>모두에게 같은 달력</b>이라, 소유 키도 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController implements HolidayApi {

    private final LeaveService leaveService;

    /**
     * 기준일을 서버 시계에서 읽는다 — 허용 범위는 "지금 기준 지난해~내년" 이라 클라이언트가 정할 값이 아니다.
     * 날짜 해석(기간스타일)과 달리 사용자의 로컬 달력이 정본일 이유가 없다.
     */
    @Override
    @GetMapping
    public ApiResponseBody<HolidayResponse> holidays(@RequestParam int year) {
        HolidayYear requested = HolidayYear.of(year, LocalDate.now());
        return ApiResponseBody.ok(HolidayResponse.of(requested.value(), leaveService.holidaysOf(requested)));
    }
}

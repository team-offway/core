package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.AvailableTimeRequest;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.leave.controller.dto.SandwichResponse;
import com.offway.core.leave.service.LeaveService;
import com.offway.core.leave.service.dto.SandwichQuery;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaves")
@RequiredArgsConstructor
public class LeaveController implements LeaveApi {

    private final LeaveService leaveService;

    @Override
    @PostMapping("/available-time")
    public ApiResponseBody<AvailableTimeResponse> availableTime(@Valid @RequestBody AvailableTimeRequest request) {
        return ApiResponseBody.ok(AvailableTimeResponse.from(leaveService.calculate(request.toCommand())));
    }

    @Override
    @GetMapping("/sandwich")
    public ApiResponseBody<SandwichResponse> sandwich(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) Double remainingLeave) {
        SandwichQuery query = new SandwichQuery(fromDate, months, remainingLeave);
        return ApiResponseBody.ok(SandwichResponse.from(leaveService.findSandwiches(query)));
    }
}

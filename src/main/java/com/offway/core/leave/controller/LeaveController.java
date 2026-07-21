package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.AvailableTimeRequest;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

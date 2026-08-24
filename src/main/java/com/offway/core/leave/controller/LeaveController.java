package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.AddLeaveUsageRequest;
import com.offway.core.leave.controller.dto.AvailableTimeRequest;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.leave.controller.dto.MyLeaveResponse;
import com.offway.core.leave.controller.dto.SandwichResponse;
import com.offway.core.leave.controller.dto.UpdateLeaveUsageRequest;
import com.offway.core.leave.controller.dto.UpdateMyLeaveRequest;
import com.offway.core.leave.service.LeaveService;
import com.offway.core.leave.service.MyLeaveService;
import com.offway.core.leave.service.dto.SandwichQuery;
import com.offway.core.user.config.LoginUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaves")
@RequiredArgsConstructor
public class LeaveController implements LeaveApi {

    private final LeaveService leaveService;
    private final MyLeaveService myLeaveService;

    @Override
    @GetMapping("/me")
    public ApiResponseBody<MyLeaveResponse> myLeave(@LoginUser UUID userId) {
        return ApiResponseBody.ok(MyLeaveResponse.from(myLeaveService.myLeave(userId)));
    }

    @Override
    @PatchMapping("/me")
    public ApiResponseBody<MyLeaveResponse> updateMyLeave(
            @LoginUser UUID userId, @Valid @RequestBody UpdateMyLeaveRequest request) {
        return ApiResponseBody.ok(
                MyLeaveResponse.from(myLeaveService.changeTotalDays(userId, request.validTotalDays())));
    }

    @Override
    @PostMapping("/me/usages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<MyLeaveResponse> addLeaveUsage(
            @LoginUser UUID userId, @Valid @RequestBody AddLeaveUsageRequest request) {
        return ApiResponseBody.created(
                MyLeaveResponse.from(myLeaveService.addUsage(userId, request.toCommand())));
    }

    @Override
    @PatchMapping("/me/usages/{usageId}")
    public ApiResponseBody<MyLeaveResponse> updateLeaveUsage(
            @LoginUser UUID userId,
            @PathVariable long usageId,
            @Valid @RequestBody UpdateLeaveUsageRequest request) {
        return ApiResponseBody.ok(
                MyLeaveResponse.from(myLeaveService.updateUsage(userId, usageId, request.toCommand())));
    }

    @Override
    @DeleteMapping("/me/usages/{usageId}")
    public ApiResponseBody<MyLeaveResponse> deleteLeaveUsage(
            @LoginUser UUID userId, @PathVariable long usageId) {
        return ApiResponseBody.ok(MyLeaveResponse.from(myLeaveService.deleteUsage(userId, usageId)));
    }

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

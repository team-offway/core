package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.service.TripOutcomeService;
import com.offway.core.itinerary.controller.dto.TripOutcomeRequest;
import com.offway.core.itinerary.controller.dto.PendingTripsResponse;
import com.offway.core.leave.controller.dto.MyLeaveResponse;
import com.offway.core.itinerary.service.CourseLeaveDeductionService;
import com.offway.core.itinerary.controller.dto.CourseLeaveDeductionRequest;
import com.offway.core.itinerary.domain.CourseScope;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.controller.dto.CourseSaveRequest;
import com.offway.core.itinerary.controller.dto.CourseSummaryResponse;
import com.offway.core.itinerary.service.CourseStorageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseStorageController implements CourseStorageApi {

    private static final String GUEST_HEADER = "X-Guest-Id";

    private final CourseStorageService courseStorageService;
    private final CourseLeaveDeductionService courseLeaveDeductionService;
    private final TripOutcomeService tripOutcomeService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<CourseResponse> save(
            @RequestHeader(GUEST_HEADER) String guestId, @Valid @RequestBody CourseSaveRequest request) {
        return ApiResponseBody.created(CourseResponse.from(courseStorageService.save(request.toCourse(guestId))));
    }

    @Override
    @GetMapping
    public ApiResponseBody<List<CourseSummaryResponse>> myCourses(
            @RequestHeader(GUEST_HEADER) String guestId,
            @RequestParam(defaultValue = "ALL") CourseScope scope) {
        return ApiResponseBody.ok(CourseSummaryResponse.listFrom(courseStorageService.myCourses(guestId, scope)));
    }

    @Override
    @GetMapping("/{courseId}")
    public ApiResponseBody<CourseResponse> course(
            @RequestHeader(GUEST_HEADER) String guestId, @PathVariable long courseId) {
        return ApiResponseBody.ok(CourseResponse.from(courseStorageService.get(guestId, courseId)));
    }

    @Override
    @DeleteMapping("/{courseId}")
    public ApiResponseBody<Void> deleteCourse(
            @RequestHeader(GUEST_HEADER) String guestId, @PathVariable long courseId) {
        courseStorageService.delete(guestId, courseId);
        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다(exception-and-response).
        return ApiResponseBody.ok(null);
    }

    @Override
    @PostMapping("/{courseId}/leave-deduction")
    public ApiResponseBody<MyLeaveResponse> deductLeave(
            @RequestHeader(GUEST_HEADER) String guestId,
            @PathVariable long courseId,
            @Valid @RequestBody CourseLeaveDeductionRequest request) {
        return ApiResponseBody.ok(MyLeaveResponse.from(
                courseLeaveDeductionService.deduct(guestId, courseId, request.halfDayStartOrFullDay())));
    }

    @Override
    @DeleteMapping("/{courseId}/leave-deduction")
    public ApiResponseBody<MyLeaveResponse> cancelLeaveDeduction(
            @RequestHeader(GUEST_HEADER) String guestId, @PathVariable long courseId) {
        return ApiResponseBody.ok(MyLeaveResponse.from(courseLeaveDeductionService.cancel(guestId, courseId)));
    }

    @Override
    @GetMapping("/pending-trips")
    public ApiResponseBody<PendingTripsResponse> pendingTrips(@RequestHeader(GUEST_HEADER) String guestId) {
        return ApiResponseBody.ok(PendingTripsResponse.from(tripOutcomeService.pending(guestId)));
    }

    @Override
    @PostMapping("/{courseId}/trip-outcome")
    public ApiResponseBody<MyLeaveResponse> answerTripOutcome(
            @RequestHeader(GUEST_HEADER) String guestId,
            @PathVariable long courseId,
            @Valid @RequestBody TripOutcomeRequest request) {
        return ApiResponseBody.ok(
                MyLeaveResponse.from(tripOutcomeService.answer(guestId, courseId, request.outcome())));
    }
}

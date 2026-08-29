package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.curation.domain.Surface;
import com.offway.core.curation.service.CurationService;
import com.offway.core.common.response.PageResponse;
import com.offway.core.itinerary.service.TripOutcomeService;
import com.offway.core.itinerary.controller.dto.TripOutcomeRequest;
import com.offway.core.itinerary.controller.dto.PendingTripsResponse;
import com.offway.core.leave.controller.dto.MyLeaveResponse;
import com.offway.core.itinerary.service.CourseLeaveDeductionService;
import com.offway.core.itinerary.domain.CourseScope;
import com.offway.core.itinerary.service.dto.MyCourses;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.controller.dto.CourseSaveRequest;
import com.offway.core.itinerary.controller.dto.CourseShareResponse;
import com.offway.core.itinerary.controller.dto.CourseSummaryResponse;
import com.offway.core.itinerary.controller.dto.CourseUpdateRequest;
import com.offway.core.itinerary.service.CourseStorageService;
import com.offway.core.user.config.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseStorageController implements CourseStorageApi {

    private final CourseStorageService courseStorageService;
    private final CourseLeaveDeductionService courseLeaveDeductionService;
    private final TripOutcomeService tripOutcomeService;
    private final CurationService curationService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<CourseResponse> save(
            @LoginUser UUID userId, @Valid @RequestBody CourseSaveRequest request) {
        return ApiResponseBody.created(CourseResponse.from(
                courseStorageService.save(request.toCourse(userId)), curationService.linksOn(Surface.COURSE)));
    }

    @Override
    @PostMapping("/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<CourseShareResponse> share(@Valid @RequestBody CourseSaveRequest request) {
        return ApiResponseBody.created(
                CourseShareResponse.from(courseStorageService.shareWithoutSaving(request.toSharedCourse())));
    }

    @Override
    @GetMapping
    public ApiResponseBody<List<CourseSummaryResponse>> myCourses(
            @LoginUser UUID userId,
            @RequestParam(defaultValue = "ALL") CourseScope scope,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        MyCourses myCourses = courseStorageService.myCourses(userId, scope, page, size);
        return ApiResponseBody.ok(CourseSummaryResponse.listFrom(myCourses), PageResponse.of(myCourses));
    }

    @Override
    @GetMapping("/{courseId}")
    public ApiResponseBody<CourseResponse> course(@LoginUser UUID userId, @PathVariable long courseId) {
        return ApiResponseBody.ok(CourseResponse.from(
                courseStorageService.get(userId, courseId), curationService.linksOn(Surface.COURSE)));
    }

    @Override
    @PatchMapping("/{courseId}")
    public ApiResponseBody<CourseResponse> updateCourse(
            @LoginUser UUID userId,
            @PathVariable long courseId,
            @Valid @RequestBody CourseUpdateRequest request) {
        return ApiResponseBody.ok(CourseResponse.from(
                courseStorageService.changeTravelDate(userId, courseId, request.travelDate()),
                curationService.linksOn(Surface.COURSE)));
    }

    @Override
    @DeleteMapping("/{courseId}")
    public ApiResponseBody<Void> deleteCourse(@LoginUser UUID userId, @PathVariable long courseId) {
        courseStorageService.delete(userId, courseId);
        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다(exception-and-response).
        return ApiResponseBody.ok(null);
    }

    @Override
    @DeleteMapping("/{courseId}/leave-deduction")
    public ApiResponseBody<MyLeaveResponse> cancelLeaveDeduction(
            @LoginUser UUID userId, @PathVariable long courseId) {
        return ApiResponseBody.ok(MyLeaveResponse.from(courseLeaveDeductionService.cancel(userId, courseId)));
    }

    @Override
    @GetMapping("/pending-trips")
    public ApiResponseBody<PendingTripsResponse> pendingTrips(@LoginUser UUID userId) {
        return ApiResponseBody.ok(PendingTripsResponse.from(tripOutcomeService.pending(userId)));
    }

    @Override
    @PostMapping("/{courseId}/trip-outcome")
    public ApiResponseBody<MyLeaveResponse> answerTripOutcome(
            @LoginUser UUID userId,
            @PathVariable long courseId,
            @Valid @RequestBody TripOutcomeRequest request) {
        return ApiResponseBody.ok(
                MyLeaveResponse.from(tripOutcomeService.answer(userId, courseId, request.outcome())));
    }
}

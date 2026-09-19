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
import com.offway.core.itinerary.domain.Origin;
import com.offway.core.transport.service.OriginSuggestService;
import com.offway.core.itinerary.controller.dto.CourseShareResponse;
import com.offway.core.itinerary.controller.dto.CourseSummaryResponse;
import com.offway.core.itinerary.controller.dto.CourseTransitModeRequest;
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
    private final OriginSuggestService originSuggestService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<CourseResponse> save(
            @LoginUser UUID userId, @Valid @RequestBody CourseSaveRequest request) {
        return ApiResponseBody.created(CourseResponse.from(
                courseStorageService.save(request.toCourse(userId, originOf(request))),
                curationService.linksOn(Surface.COURSE)));
    }

    @Override
    @PostMapping("/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<CourseShareResponse> share(@Valid @RequestBody CourseSaveRequest request) {
        return ApiResponseBody.created(CourseShareResponse.from(
                courseStorageService.shareWithoutSaving(request.toSharedCourse(originOf(request)))));
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
    @PatchMapping("/{courseId}/transit-mode")
    public ApiResponseBody<CourseResponse> updateTransitMode(
            @LoginUser UUID userId,
            @PathVariable long courseId,
            @Valid @RequestBody CourseTransitModeRequest request) {
        return ApiResponseBody.ok(CourseResponse.from(
                courseStorageService.changeTransitMode(userId, courseId, request.transitMode()),
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
        return ApiResponseBody.ok(MyLeaveResponse.from(
                tripOutcomeService.answer(userId, courseId, request.outcome(), request.toFeedback())));
    }

    /**
     * 출발지 코드를 풀어 도메인 출발지로 옮긴다 — 코드가 없거나 못 풀면 {@code null}(#590).
     *
     * <p><b>여기서는 못 푼 코드를 거절하지 않는다.</b> 생성·추천과 갈리는 자리다 — 그쪽은 출발지가
     * 코스의 내용을 바꾸므로 틀린 값으로 짜는 것보다 다시 고르게 하는 편이 낫다. 담기는 이미 만든
     * 코스를 저장하는 일이라, 곁가지 값 하나 때문에 담기가 실패하면 주객이 뒤집힌다(#382 가 이름을
     * 버리고 계속 담는 판단을 한 것과 같다). 좌표 경로로 떨어지고, 그것도 없으면 출발지 없이 담는다.
     */
    private Origin originOf(CourseSaveRequest request) {
        if (request.originCode() == null || request.originCode().isBlank()) {
            return null;
        }
        return originSuggestService
                .resolve(new com.offway.core.transport.domain.OriginCode(request.originCode().trim()),
                        request.originName())
                .map(resolved -> Origin.of(resolved.coordinate(), resolved.name()))
                .orElse(null);
    }
}

package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.controller.dto.CourseSaveRequest;
import com.offway.core.itinerary.controller.dto.CourseSummaryResponse;
import com.offway.core.itinerary.service.CourseStorageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseStorageController implements CourseStorageApi {

    private static final String GUEST_HEADER = "X-Guest-Id";

    private final CourseStorageService courseStorageService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<CourseResponse> save(
            @RequestHeader(GUEST_HEADER) String guestId, @Valid @RequestBody CourseSaveRequest request) {
        return ApiResponseBody.created(CourseResponse.from(courseStorageService.save(guestId, request)));
    }

    @Override
    @GetMapping
    public ApiResponseBody<List<CourseSummaryResponse>> myCourses(@RequestHeader(GUEST_HEADER) String guestId) {
        return ApiResponseBody.ok(
                courseStorageService.myCourses(guestId).stream().map(CourseSummaryResponse::from).toList());
    }

    @Override
    @GetMapping("/{courseId}")
    public ApiResponseBody<CourseResponse> course(@PathVariable long courseId) {
        return ApiResponseBody.ok(CourseResponse.from(courseStorageService.get(courseId)));
    }
}

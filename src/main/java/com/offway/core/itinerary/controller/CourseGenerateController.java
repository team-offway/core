package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.service.CourseRegenerationService;
import com.offway.core.itinerary.controller.dto.CourseRegenerateResponse;
import com.offway.core.itinerary.controller.dto.CourseRegenerateRequest;
import com.offway.core.itinerary.controller.dto.CourseGenerateRequest;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.service.CourseGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseGenerateController implements CourseGenerateApi {

    private final CourseGenerationService courseGenerationService;
    private final CourseRegenerationService courseRegenerationService;

    @Override
    @PostMapping("/generate")
    public ApiResponseBody<CourseResponse> generate(@Valid @RequestBody CourseGenerateRequest request) {
        return ApiResponseBody.ok(CourseResponse.from(courseGenerationService.generate(request.toCommand())));
    }

    @Override
    @PostMapping("/regenerate")
    public ApiResponseBody<CourseRegenerateResponse> regenerate(@Valid @RequestBody CourseRegenerateRequest request) {
        return ApiResponseBody.ok(CourseRegenerateResponse.from(courseRegenerationService.regenerate(
                request.toCommand(), request.seed(), request.previousSeed())));
    }
}

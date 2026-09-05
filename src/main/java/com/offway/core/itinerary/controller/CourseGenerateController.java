package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.curation.domain.Surface;
import com.offway.core.curation.service.CurationService;
import com.offway.core.itinerary.service.CourseRegenerationService;
import com.offway.core.itinerary.controller.dto.CourseRegenerateResponse;
import com.offway.core.itinerary.controller.dto.CourseRegenerateRequest;
import com.offway.core.itinerary.controller.dto.CourseGenerateRequest;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.service.CourseGenerationService;
import com.offway.core.user.config.LoginUser;
import jakarta.validation.Valid;
import java.util.UUID;
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
    private final CurationService curationService;

    @Override
    @PostMapping("/generate")
    public ApiResponseBody<CourseResponse> generate(
            @LoginUser UUID userId, @Valid @RequestBody CourseGenerateRequest request) {
        return ApiResponseBody.ok(CourseResponse.from(
                courseGenerationService.generate(request.toCommand(), userId), curationService.linksOn(Surface.COURSE)));
    }

    @Override
    @PostMapping("/regenerate")
    public ApiResponseBody<CourseRegenerateResponse> regenerate(
            @LoginUser UUID userId, @Valid @RequestBody CourseRegenerateRequest request) {
        return ApiResponseBody.ok(CourseRegenerateResponse.from(
                courseRegenerationService.regenerate(
                        request.toCommand(), request.seed(), request.previousSeed(), userId),
                curationService.linksOn(Surface.COURSE)));
    }
}

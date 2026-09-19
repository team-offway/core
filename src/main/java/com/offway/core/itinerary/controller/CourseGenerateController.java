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
import com.offway.core.transport.service.OriginSuggestService;
import com.offway.core.transport.service.dto.ResolvedOrigin;
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
    private final OriginSuggestService originSuggestService;

    @Override
    @PostMapping("/generate")
    public ApiResponseBody<CourseResponse> generate(
            @LoginUser UUID userId, @Valid @RequestBody CourseGenerateRequest request) {
        // 출발지를 여기서 푼다 — 세 형태(코드·좌표·기본값)를 커맨드 하나로 모으는 입력 해석이라
        // 컨트롤러 자리가 맞다. 우선순위 판단은 OriginSuggestService 가 소유하고, 이 줄은 그 결과를
        // 커맨드에 옮기기만 한다. 서비스마다 넣으면 itinerary·trip 두 곳에 같은 규칙이 복사된다.
        ResolvedOrigin origin = originSuggestService.resolveOrDefault(
                request.originCode(), request.originName(), request.originLat(), request.originLng());
        return ApiResponseBody.ok(CourseResponse.from(
                courseGenerationService.generate(request.toCommand(origin), userId),
                curationService.linksOn(Surface.COURSE)));
    }

    @Override
    @PostMapping("/regenerate")
    public ApiResponseBody<CourseRegenerateResponse> regenerate(
            @LoginUser UUID userId, @Valid @RequestBody CourseRegenerateRequest request) {
        ResolvedOrigin origin = originSuggestService.resolveOrDefault(
                request.originCode(), request.originName(), request.originLat(), request.originLng());
        return ApiResponseBody.ok(CourseRegenerateResponse.from(
                courseRegenerationService.regenerate(
                        request.toCommand(origin), request.seed(), request.previousSeed(), userId),
                curationService.linksOn(Surface.COURSE)));
    }
}

package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.curation.domain.Surface;
import com.offway.core.curation.service.CurationService;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.service.CourseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공유 링크로 코스를 여는 <b>인증 없는</b> 경로(#143).
 *
 * <p>베이스가 {@code /api/v1/public} 인 이유: 인증 예외를 <b>경로 접두어 하나로</b> 좁히기 위해서다.
 * 엔드포인트마다 예외를 흩으면 어느 것이 열려 있는지 한눈에 안 보이고, 새 엔드포인트가 실수로 열린다.
 *
 * <p>이 아래에 오는 것은 전부 <b>보기 전용</b>이고 소유자 식별자를 반환하지 않는다. 쓰기나 개인 데이터가
 * 필요하면 이 접두어에 두지 않는다.
 */
@RestController
@RequestMapping("/api/v1/public/courses")
@RequiredArgsConstructor
public class PublicCourseController implements PublicCourseApi {

    private final CourseStorageService courseStorageService;
    private final CurationService curationService;

    @Override
    @GetMapping("/{shareToken}")
    public ApiResponseBody<CourseResponse> shared(@PathVariable String shareToken) {
        return ApiResponseBody.ok(CourseResponse.publicView(
                courseStorageService.findShared(shareToken), curationService.linksOn(Surface.COURSE)));
    }
}

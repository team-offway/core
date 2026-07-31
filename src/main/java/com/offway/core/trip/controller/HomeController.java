package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.HomeResponse;
import com.offway.core.trip.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController implements HomeApi {

    /** 소유 키 헤더 — 남은 연차를 읽는다. 없으면 남은 연차는 null 이고 나머지 홈은 그대로 뜬다. */
    private static final String GUEST_HEADER = "X-Guest-Id";

    private final HomeService homeService;

    @Override
    @GetMapping
    public ApiResponseBody<HomeResponse> home(
            @RequestHeader(value = GUEST_HEADER, required = false) String guestId) {
        return ApiResponseBody.ok(HomeResponse.from(homeService.home(guestId)));
    }
}

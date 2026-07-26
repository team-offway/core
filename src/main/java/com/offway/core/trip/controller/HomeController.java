package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.HomeResponse;
import com.offway.core.trip.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController implements HomeApi {

    private final HomeService homeService;

    @Override
    @GetMapping
    public ApiResponseBody<HomeResponse> home(@RequestParam(required = false) Integer remainingLeave) {
        return ApiResponseBody.ok(HomeResponse.from(homeService.home(remainingLeave)));
    }
}

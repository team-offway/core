package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.HomeResponse;
import com.offway.core.trip.service.HomeService;
import com.offway.core.user.config.LoginUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController implements HomeApi {

    private final HomeService homeService;

    /**
     * 홈은 <b>로그인 없이도 뜬다</b> — 지역 둘러보기가 로그인 앞에 있는 화면이라 소유자 식별자를 요구하지 않는다.
     * 그래서 {@code userId} 는 <b>선택</b>이다(비로그인 요청에서는 null). 남은 연차만 null 로 내려가고 나머지
     * 카드는 그대로 채워진다.
     */
    @Override
    @GetMapping
    public ApiResponseBody<HomeResponse> home(@LoginUser UUID userId) {
        return ApiResponseBody.ok(HomeResponse.from(homeService.home(userId)));
    }
}

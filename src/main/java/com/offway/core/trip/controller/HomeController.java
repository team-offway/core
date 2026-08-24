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
     * 홈은 <b>access 토큰 없이도 뜬다</b> — 지역 둘러보기가 로그인 앞에 있는 화면이라 소유자를 요구하지 않는다.
     * 그래서 {@code userId} 는 <b>선택</b>이고, 못 정하면 남은 연차만 null 로 내려가고 카드는 그대로 채워진다.
     *
     * <p><b>자격증명이 아예 없는 요청은 여기까지 오지 않는다.</b> {@code SecurityConfig} 가 모든 GET 을
     * 인증 뒤에 두므로(#122) 그건 401 이다. 여기 닿는 "주인 없는 요청" 은 Basic 게이트로 들어와
     * {@code ROLE_USER} 가 없는 쪽이다.
     */
    @Override
    @GetMapping
    public ApiResponseBody<HomeResponse> home(@LoginUser UUID userId) {
        return ApiResponseBody.ok(HomeResponse.from(homeService.home(userId)));
    }
}

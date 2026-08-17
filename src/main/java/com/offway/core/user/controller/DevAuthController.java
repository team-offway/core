package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.controller.dto.DevLoginRequest;
import com.offway.core.user.controller.dto.TokenResponse;
import com.offway.core.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발용 로그인 — OAuth 를 강제하면 FE 가 로컬에서 실 provider 토큰 없이는 어떤 API 도 부를 수 없기 때문에 둔다.
 *
 * <p>{@code @Profile("local")} 이라 prod 에는 빈이 아예 없다. 경로가 열려 있는데 막는 방식이 아니라, 존재하지 않는 방식이다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Profile("local")
public class DevAuthController implements DevAuthApi {

    private final AuthService authService;

    @Override
    @PostMapping("/dev-login")
    public ApiResponseBody<TokenResponse> devLogin(@Valid @RequestBody DevLoginRequest request) {
        return ApiResponseBody.ok(TokenResponse.from(authService.devLogin(request.nickname())));
    }
}

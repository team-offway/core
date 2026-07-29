package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.config.LoginUser;
import com.offway.core.user.controller.dto.LoginRequest;
import com.offway.core.user.controller.dto.ReissueRequest;
import com.offway.core.user.controller.dto.TokenResponse;
import com.offway.core.user.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    /** 신규 가입도 201 이 아니라 200 — 생기는 건 세션이지 클라이언트가 URL 로 가리킬 리소스가 아니다. */
    @Override
    @PostMapping("/login")
    public ApiResponseBody<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponseBody.ok(TokenResponse.from(authService.login(request.toCommand())));
    }

    @Override
    @PostMapping("/reissue")
    public ApiResponseBody<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponseBody.ok(TokenResponse.from(authService.reissue(request.refreshToken())));
    }

    @Override
    @PostMapping("/logout")
    public ApiResponseBody<Void> logout(@LoginUser UUID userId) {
        authService.logout(userId);
        return ApiResponseBody.ok();
    }
}

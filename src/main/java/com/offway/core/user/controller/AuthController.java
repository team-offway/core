package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.config.LoginUser;
import com.offway.core.user.controller.dto.ReissueRequest;
import com.offway.core.user.controller.dto.SocialLoginRequest;
import com.offway.core.user.controller.dto.TokenResponse;
import com.offway.core.user.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    /**
     * 신규 가입도 201 이 아니라 200 — 생기는 건 세션이지 클라이언트가 URL 로 가리킬 리소스가 아니다.
     *
     * <p>provider 는 본문이 아니라 경로에 둔다. 앱이 provider 별로 다른 화면·다른 SDK 를 타고 들어오므로 경로가
     * 갈리는 편이 호출부에서 읽히고, 서버도 라우팅만 보고 어느 provider 인지 안다.
     */
    @Override
    @PostMapping("/callback/{provider}")
    public ApiResponseBody<TokenResponse> callback(
            @PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
        return ApiResponseBody.ok(TokenResponse.from(authService.login(request.toCommand(provider))));
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

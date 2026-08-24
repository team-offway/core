package com.offway.core.device.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.device.controller.dto.DeviceRegisterRequest;
import com.offway.core.device.service.DeviceService;
import com.offway.core.user.config.LoginUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController implements DeviceApi {

    private final DeviceService deviceService;

    @Override
    @PostMapping
    public ApiResponseBody<Void> register(
            @LoginUser UUID userId, @Valid @RequestBody DeviceRegisterRequest request) {
        // 소유 키를 사람으로 맞춘다(#280). 기기를 가리키는 것은 token 이고, 이 칸은 "누구의 기기냐" 다 —
        // 알림이 user_id 로 만들어지므로 발송이 토큰을 찾으려면 같은 값이어야 한다.
        deviceService.register(request.toRegistration(userId.toString()));
        // 201 이 아니다 — 새로 만드는지 고쳐 쓰는지가 요청마다 달라 만들었다고 단정할 수 없다.
        return ApiResponseBody.ok();
    }

    @Override
    @DeleteMapping
    public ApiResponseBody<Void> unregister(@LoginUser UUID userId) {
        deviceService.unregister(userId.toString());
        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다(exception-and-response).
        return ApiResponseBody.ok();
    }
}

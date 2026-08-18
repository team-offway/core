package com.offway.core.device.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.device.controller.dto.DeviceRegisterRequest;
import com.offway.core.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController implements DeviceApi {

    /** 소유 키 — 코스·연차와 같은 헤더를 쓴다. 인증이 붙는 날 함께 옮긴다. */
    private static final String GUEST_HEADER = "X-Guest-Id";

    private final DeviceService deviceService;

    @Override
    @PostMapping
    public ApiResponseBody<Void> register(
            @RequestHeader(GUEST_HEADER) String guestId, @Valid @RequestBody DeviceRegisterRequest request) {
        deviceService.register(request.toRegistration(guestId));
        // 201 이 아니다 — 새로 만드는지 고쳐 쓰는지가 요청마다 달라 만들었다고 단정할 수 없다.
        return ApiResponseBody.ok();
    }

    @Override
    @DeleteMapping
    public ApiResponseBody<Void> unregister(@RequestHeader(GUEST_HEADER) String guestId) {
        deviceService.unregister(guestId);
        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다(exception-and-response).
        return ApiResponseBody.ok();
    }
}

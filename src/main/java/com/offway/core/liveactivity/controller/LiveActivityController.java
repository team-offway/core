package com.offway.core.liveactivity.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.liveactivity.controller.dto.LiveActivityRegisterRequest;
import com.offway.core.liveactivity.controller.dto.PushToStartRegisterRequest;
import com.offway.core.liveactivity.service.LiveActivityService;
import com.offway.core.liveactivity.service.PushToStartService;
import com.offway.core.user.config.LoginUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/live-activities")
@RequiredArgsConstructor
public class LiveActivityController implements LiveActivityApi {

    private final LiveActivityService liveActivityService;
    private final PushToStartService pushToStartService;

    @Override
    @PostMapping
    public ApiResponseBody<Void> register(
            @LoginUser UUID userId, @Valid @RequestBody LiveActivityRegisterRequest request) {
        liveActivityService.register(userId, request.courseId(), request.pushToken());
        // 201 이 아니다 — 새로 만드는지 고쳐 쓰는지가 요청마다 달라 만들었다고 단정할 수 없다.
        return ApiResponseBody.ok();
    }

    @Override
    @DeleteMapping("/{courseId}")
    public ApiResponseBody<Void> unregister(@LoginUser UUID userId, @PathVariable Long courseId) {
        liveActivityService.unregister(userId, courseId);
        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다(exception-and-response).
        return ApiResponseBody.ok();
    }

    // `/push-to-start` 가 위의 `/{courseId}` 보다 **먼저 잡힌다** — 글자로 적힌 경로가 변수 경로를
    // 이긴다(PathPatternParser). 순서에 기대는 것이 아니라 규격이라 뒤에 둬도 안전하다.

    @Override
    @PutMapping("/push-to-start")
    public ApiResponseBody<Void> registerPushToStart(
            @LoginUser UUID userId, @Valid @RequestBody PushToStartRegisterRequest request) {
        pushToStartService.register(userId, request.token());
        return ApiResponseBody.ok();
    }

    @Override
    @DeleteMapping("/push-to-start")
    public ApiResponseBody<Void> unregisterPushToStart(@LoginUser UUID userId) {
        pushToStartService.unregisterAll(userId);
        return ApiResponseBody.ok();
    }
}

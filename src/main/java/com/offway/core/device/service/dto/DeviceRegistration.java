package com.offway.core.device.service.dto;

import com.offway.core.device.domain.DevicePlatform;

/**
 * 푸시 토큰 등록 커맨드(#264) — 요청 DTO 와 도메인 사이의 내부 표현.
 *
 * @param guestId 소유 키
 * @param token FCM 토큰. 비밀값에 준하므로 로그에 남기지 않는다
 * @param platform 기기 종류
 */
public record DeviceRegistration(String guestId, String token, DevicePlatform platform) {
}

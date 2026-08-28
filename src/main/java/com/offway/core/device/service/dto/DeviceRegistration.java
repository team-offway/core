package com.offway.core.device.service.dto;

import com.offway.core.device.domain.DevicePlatform;
import lombok.Builder;

/**
 * 푸시 토큰 등록 커맨드(#264) — 요청 DTO 와 도메인 사이의 내부 표현.
 *
 * <p><b>조립이라 빌더다</b>(#300). 소유 키와 토큰이 둘 다 String 으로 나란히 있어 위치 인수로
 * 넘기면 뒤바뀌어도 컴파일이 통과한다. 그러면 발송이 기기를 못 찾아 <b>알림은 만들어지는데
 * 푸시만 조용히 안 간다</b> — 예외도 로그도 없다.
 *
 * @param guestId 소유 키
 * @param token FCM 토큰. 비밀값에 준하므로 로그에 남기지 않는다
 * @param platform 기기 종류
 */
@Builder
public record DeviceRegistration(String guestId, String token, DevicePlatform platform) {
}

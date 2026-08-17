package com.offway.core.device.service;

import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.repository.DevicePushTokenRepository;
import com.offway.core.device.service.dto.DeviceRegistration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 토큰 등록·해제(#264).
 *
 * <p>여기서 알림을 보내지 않는다 — 받을 주소를 보관하는 데까지다. 실제 발송은 별도 작업이다.
 *
 * <p><b>로그에 토큰을 남기지 않는다.</b> 이 값을 아는 쪽은 그 기기로 알림을 보낼 수 있어 비밀값에 준한다
 * (로깅 규약). 플랫폼과 건수만 남긴다.
 *
 * <p>외부 호출이 없어 트랜잭션이 짧다 — 전부 DB 만 만진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    /** 등록·갱신 시각 기준 시간대. 서비스가 한국 여행을 다루므로 사용자 로캘과 무관하게 KST 다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DevicePushTokenRepository devicePushTokenRepository;

    /**
     * 토큰을 등록하거나 이미 있으면 갱신한다.
     *
     * <p><b>있는지 먼저 보지 않는다.</b> 조회 후 분기하면 같은 기기가 두 번 동시에 등록할 때 둘 다
     * "없다" 를 읽고 하나가 유니크 제약에 걸린다. 판정은 제약을 쥔 DB 가 한 문장 안에서 한다.
     *
     * <p>도메인이 값을 먼저 만든다 — 소유 키·토큰의 계약 검증이 그 안에 있어, 잘못된 입력이 DB 까지
     * 내려가지 않는다.
     */
    @Transactional
    public void register(DeviceRegistration registration) {
        DevicePushToken pushToken = DevicePushToken.register(
                registration.guestId(),
                registration.token(),
                registration.platform(),
                LocalDateTime.now(SERVICE_ZONE));
        devicePushTokenRepository.register(pushToken);
        log.info("푸시 토큰 등록 platform={}", registration.platform());
    }

    /**
     * 소유자의 푸시 토큰을 전부 해제한다 — 로그아웃·알림 끄기.
     *
     * <p><b>지울 것이 없어도 성공이다.</b> 원한 상태("이 소유자에게 알림이 가지 않는다")가 이미 이뤄져
     * 있고, 로그아웃 화면이 404 를 띄울 이유가 없다.
     */
    @Transactional
    public void unregister(String guestId) {
        int deleted = devicePushTokenRepository.deleteByOwner(DevicePushToken.requireOwner(guestId));
        log.info("푸시 토큰 해제 deleted={}", deleted);
    }
}

package com.offway.core.device.repository;

import com.offway.core.device.domain.DevicePushToken;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class DevicePushTokenRepositoryImpl implements DevicePushTokenRepository {

    private final DevicePushTokenJpaRepository devicePushTokenJpaRepository;

    /**
     * <b>쓰기는 어댑터가 트랜잭션을 연다.</b> {@code upsert} 는 {@code @Modifying} native 질의라 트랜잭션
     * 없이는 못 돈다. 운영 경로는 {@code DeviceService} 가 트랜잭션을 갖고 있어 지금까지 드러나지 않았지만,
     * 그건 호출자가 누구냐에 기댄 것이다 — 테스트가 저장소를 직접 부르는 순간
     * {@code TransactionRequiredException} 이 난다({@code PushDispatcherIntegrationTest} 가 그랬다).
     *
     * <p>이미 트랜잭션 안이면 그대로 참여하므로 운영 동작은 달라지지 않는다.
     */
    @Override
    @Transactional
    public void register(DevicePushToken devicePushToken) {
        devicePushTokenJpaRepository.upsert(
                devicePushToken.getGuestId(),
                devicePushToken.getToken(),
                devicePushToken.getPlatform().name(),
                devicePushToken.getUpdatedAt());
    }

    /** {@code deleteByGuestId} 도 {@code @Modifying} 이다 — 위와 같은 이유로 경계를 여기서 연다. */
    @Override
    @Transactional
    public int deleteByOwner(String guestId) {
        return devicePushTokenJpaRepository.deleteByGuestId(guestId);
    }

    /**
     * 발송 경로는 <b>트랜잭션 밖</b>이라(외부 호출) 여기서 경계를 만든다. {@code @Modifying} 질의는
     * 트랜잭션 없이는 못 돈다.
     */
    @Override
    @Transactional
    public int deleteByToken(String token) {
        return devicePushTokenJpaRepository.deleteByToken(token);
    }

    @Override
    public List<DevicePushToken> findByOwner(String guestId) {
        return devicePushTokenJpaRepository.findByGuestIdOrderByIdAsc(guestId);
    }
}

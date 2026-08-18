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

    @Override
    public void register(DevicePushToken devicePushToken) {
        devicePushTokenJpaRepository.upsert(
                devicePushToken.getGuestId(),
                devicePushToken.getToken(),
                devicePushToken.getPlatform().name(),
                devicePushToken.getUpdatedAt());
    }

    @Override
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

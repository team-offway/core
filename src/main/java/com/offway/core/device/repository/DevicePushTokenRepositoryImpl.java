package com.offway.core.device.repository;

import com.offway.core.device.domain.DevicePushToken;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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

    @Override
    public List<DevicePushToken> findByOwner(String guestId) {
        return devicePushTokenJpaRepository.findByGuestIdOrderByIdAsc(guestId);
    }
}

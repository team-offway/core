package com.offway.core.device.repository;

import com.offway.core.device.domain.DevicePushToken;
import java.util.List;

/** 푸시 토큰 영속 port(#264). 구현은 {@link DevicePushTokenRepositoryImpl}. */
public interface DevicePushTokenRepository {

    /**
     * 등록하거나, 이미 있는 토큰이면 갱신한다 — <b>한 문장으로</b>.
     *
     * <p>"있나 보고 없으면 넣기" 로 풀지 않는다. 같은 기기가 두 번 동시에 등록하면 둘 다 "없다" 를 읽고
     * 하나가 유니크 제약에 걸린다. 판정을 제약을 쥔 DB 에 맡기면 경합이 애초에 생기지 않는다.
     *
     * <p>갱신 대상은 소유자·플랫폼·갱신 시각이다. <b>소유자를 덮어쓰는 것이 핵심이다</b> — 앱을 지웠다
     * 깔면 게스트 ID 는 새로 발급되지만 토큰은 이어질 수 있고, 그때 이 기기의 주인은 새 게스트다.
     * 처음 등록 시각은 남긴다.
     */
    void register(DevicePushToken devicePushToken);

    /**
     * 소유자의 푸시 토큰을 전부 지운다 — 로그아웃·알림 끄기.
     *
     * @return 지운 건수
     */
    int deleteByOwner(String guestId);

    /** 소유자의 푸시 토큰들. 알림을 보낼 주소를 꺼내는 읽기 경로다. */
    List<DevicePushToken> findByOwner(String guestId);
}

package com.offway.core.device.repository;

import com.offway.core.device.domain.DevicePushToken;
import java.util.List;

/** 푸시 토큰 영속 port(#264). 구현은 {@link DevicePushTokenRepositoryImpl}. */
public interface DevicePushTokenRepository {

    /**
     * 등록하거나, 이미 있는 토큰이면 갱신한다 — <b>한 문장으로</b>.
     *
     * <p>"있나 보고 없으면 넣기" 로 풀지 않는다. 같은 기기가 두 번 동시에 등록하면 둘 다 "없다" 를 읽고
     * 하나가 유니크 제약에 걸린다. 판정을 제약을 쥔 DB 에 맡기면 경합을 DB 가 흡수한다.
     *
     * <p><b>같은 것으로 보는 기준은 {@code (소유자, 토큰)} 이다.</b> 같은 소유자가 같은 토큰을 다시
     * 보내면 행이 늘지 않고 플랫폼·갱신 시각만 새로 쓴다(처음 등록 시각은 남긴다). 같은 토큰이 다른
     * 소유자로 오면 <b>남의 행을 건드리지 않고</b> 새 행이 된다.
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

    /**
     * 그 토큰으로 등록된 행을 <b>소유자를 가리지 않고</b> 전부 지운다 — FCM 이 죽었다고 답한 토큰 정리(#270).
     *
     * <p>소유자 범위로 지우지 않는 이유는 재설치 때문이다. 게스트 ID 가 새로 발급되면 같은 토큰이 여러
     * 소유자로 남는데, FCM 이 그 토큰을 {@code UNREGISTERED} 로 답했다면 <b>그 행들이 전부 죽은 것</b>이다.
     * 한 행만 지우면 나머지가 남아 다음 발송에서 또 실패한다.
     *
     * @return 지운 건수
     */
    int deleteByToken(String token);
}

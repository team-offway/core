package com.offway.core.user.infrastructure.apple;

import java.util.List;
import java.util.Optional;

/**
 * Apple 계정 연결 port(#287) — 토큰 교환과 연결 해제.
 *
 * <p><b>왜 필요한가.</b> Apple 은 계정 삭제를 지원하는 앱에 토큰 revoke 를 요구한다(App Store 심사 5.1.1(v)).
 * 우리 DB 만 지우면 Apple 의 '이 App으로 로그인' 목록에는 그대로 남는다.
 *
 * <p><b>왜 로그인 때 교환해야 하는가.</b> {@code /auth/revoke} 는 refresh 토큰을 요구하는데, 그것을 얻으려면
 * {@code authorizationCode} 가 필요하고 그 코드는 <b>1회용·5분</b>이다. 탈퇴 시점에는 이미 없다.
 */
public interface AppleAccountLink {

    /**
     * 로그인 때 받은 코드를 refresh 토큰으로 바꾼다.
     *
     * <p><b>실패해도 예외를 던지지 않는다.</b> 이 호출은 로그인 경로에 붙는데, 여기서 던지면 Apple 토큰
     * 엔드포인트가 흔들릴 때 <b>로그인 자체가 막힌다</b> — 연결 해제를 못 하는 것보다 훨씬 나쁘다.
     * 못 얻으면 비어 있는 값을 돌려주고 사유를 로그로 남긴다.
     *
     * @param clientId 이 토큰을 발급받은 클라이언트(Bundle ID 또는 Service ID)
     * @return Apple refresh 토큰. 자격 미설정·교환 실패면 빈 값
     */
    Optional<String> exchange(String authorizationCode, String clientId);

    /**
     * 연결을 끊는다 — Apple 의 '이 App으로 로그인' 목록에서 사라진다.
     *
     * @param refreshToken 로그인 때 저장해 둔 Apple refresh 토큰
     * @param clientId 그 토큰을 발급받은 클라이언트
     * @return 실제로 끊었으면 true. 자격 미설정·호출 실패면 false — 탈퇴는 그대로 진행한다
     */
    boolean revoke(String refreshToken, String clientId);

    /** 우리 클라이언트 식별자들 — 어느 것으로 발급됐는지 모를 때 순서대로 시도한다. */
    List<String> clientIds();
}

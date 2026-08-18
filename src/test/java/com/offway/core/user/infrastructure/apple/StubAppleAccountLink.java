package com.offway.core.user.infrastructure.apple;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Apple 계정 연결 외부 경계 stub — 통합 테스트에서 {@code appleid.apple.com} 호출을 격리한다(#287).
 *
 * <p><b>default 는 "자격 없음" 이다</b>(throw 가 아니다). 이 port 는 로그인·탈퇴 <b>모든</b> 경로가 지나가는데,
 * 대부분의 시나리오는 Apple 과 무관하다(구글 로그인·코스 테스트 등). default 를 throw 로 두면 그 전부가
 * Apple 설정을 강제당한다. 자격이 없을 때의 실제 동작이 "조용히 건너뛴다" 이므로 그것이 안전한 기본값이다.
 *
 * <p>대신 <b>무엇이 불렸는지 기록한다</b> — 건너뛴 것과 부른 것을 테스트가 구분할 수 있어야 한다.
 */
public class StubAppleAccountLink implements AppleAccountLink {

    /** 실물과 같은 후보 순서 — Service ID 먼저, Bundle ID 다음. */
    private static final List<String> DEFAULT_CLIENT_IDS = List.of("com.nth.offway.service", "com.nth.offway");

    private List<String> clientIds = DEFAULT_CLIENT_IDS;

    private BiFunction<String, String, Optional<String>> exchange = (code, clientId) -> Optional.empty();

    private BiFunction<String, String, Boolean> revoke = (token, clientId) -> false;

    private final List<String> revokedTokens = new ArrayList<>();

    private final List<String> exchangedClientIds = new ArrayList<>();

    private final List<String> revokedClientIds = new ArrayList<>();

    @Override
    public List<String> clientIds() {
        return clientIds;
    }

    @Override
    public Optional<String> exchange(String authorizationCode, String clientId) {
        exchangedClientIds.add(clientId);
        return exchange.apply(authorizationCode, clientId);
    }

    @Override
    public boolean revoke(String refreshToken, String clientId) {
        revokedTokens.add(refreshToken);
        revokedClientIds.add(clientId);
        return revoke.apply(refreshToken, clientId);
    }

    /** 어떤 코드든 이 토큰으로 교환된다 — 로그인이 토큰을 저장하는지 볼 때. */
    public void exchangesTo(String refreshToken) {
        this.exchange = (code, clientId) -> Optional.of(refreshToken);
    }

    /** 교환이 실패한다 — 자격 미설정·Apple 장애. 로그인은 그대로여야 한다. */
    public void exchangeFails() {
        this.exchange = (code, clientId) -> Optional.empty();
    }

    /** 해제가 성공한다. */
    public void revokeSucceeds() {
        this.revoke = (token, clientId) -> true;
    }

    /** 해제가 실패한다 — 탈퇴는 그대로 끝나야 한다. */
    public void revokeFails() {
        this.revoke = (token, clientId) -> false;
    }

    /**
     * 실제로 교환을 시도한 클라이언트들 — <b>몇 번</b> 시도했는지가 중요하다(#287).
     *
     * <p>{@code authorizationCode} 는 1회용이라, 틀린 클라이언트로 한 번 쓰면 맞는 쪽으로 다시 시도해도
     * 늦을 수 있다. 검증된 {@code aud} 가 있으면 <b>딱 하나</b>여야 한다.
     */
    public List<String> exchangedClientIds() {
        return List.copyOf(exchangedClientIds);
    }

    /**
     * 실제로 해제를 시도한 클라이언트들 — 발급 때와 <b>같은</b> 것이어야 Apple 이 받아준다(#287).
     */
    public List<String> revokedClientIds() {
        return List.copyOf(revokedClientIds);
    }

    /** 실제로 해제를 시도한 토큰들 — 건너뛴 것과 부른 것을 가른다. */
    public List<String> revokedTokens() {
        return List.copyOf(revokedTokens);
    }

    /** 시나리오마다 기본 상태로 되돌린다 — 앞 테스트가 남긴 설정이 새어 들지 않게. */
    public void reset() {
        this.clientIds = DEFAULT_CLIENT_IDS;
        this.exchange = (code, clientId) -> Optional.empty();
        this.revoke = (token, clientId) -> false;
        this.revokedTokens.clear();
        this.exchangedClientIds.clear();
        this.revokedClientIds.clear();
    }
}

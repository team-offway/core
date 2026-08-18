package com.offway.core.user.infrastructure.oidc;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * 공개키가 없는 JWKS 응답을 <b>성공으로 넘기지 않는다</b>(#34).
 *
 * <p>provider 가 {@code 200 {"keys":[]}} 를 주면 파싱은 성공하고 캐시에는 그 빈 묶음이 그대로 들어간다 —
 * Nimbus 의 캐시 계층은 비었는지 보지 않는다. 이 프로젝트가 금하는 모양이다: <b>빈 응답을 성공으로 캐시하지
 * 않는다.</b> 값이 없다는 점에서 실패와 결과가 같은데 성공으로 눌러 두면 무의미한 상태가 그만큼 굳는다.
 *
 * <p><b>이것이 없어도 로그인이 영구히 막히지는 않는다.</b> 키를 못 찾은 요청은 강제 갱신을 유발하므로
 * ({@code JWKSetBasedJWKSource} 가 no-match 에서 한 번 더 조회한다) 캐시 수명이 다 차기를 기다리지는 않는다.
 * 대신 그 강제 갱신이 rate limit 에 걸려 <b>원인과 무관한 이름의 실패</b>로 끝난다 — 실제 원인은 "provider 가
 * 키를 안 줬다" 인데 로그에는 "갱신 간격을 넘겼다" 만 남는다.
 *
 * <p>그래서 여기서 끊고 이유를 남긴다. 조용히 degrade 하지 않는 쪽이 낫다.
 */
@Slf4j
final class NonEmptyJwkSetSource<C extends SecurityContext> implements JWKSetSource<C> {

    private final JWKSetSource<C> delegate;

    NonEmptyJwkSetSource(JWKSetSource<C> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "위임할 JWKS 소스는 필수입니다");
    }

    @Override
    public JWKSet getJWKSet(JWKSetCacheRefreshEvaluator refreshEvaluator, long currentTime, C context)
            throws KeySourceException {
        JWKSet jwkSet = delegate.getJWKSet(refreshEvaluator, currentTime, context);
        if (jwkSet == null || jwkSet.isEmpty()) {
            log.warn("provider 가 공개키 없는 JWKS 를 돌려줬다 — 캐시하지 않고 실패로 돌린다");
            throw new KeySourceException("JWKS 에 공개키가 없습니다");
        }
        return jwkSet;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}

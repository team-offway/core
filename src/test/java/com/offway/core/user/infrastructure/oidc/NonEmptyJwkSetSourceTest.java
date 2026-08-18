package com.offway.core.user.infrastructure.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 빈 JWKS 를 성공으로 캐시하지 않는가(#34).
 *
 * <p>Nimbus 의 캐시 계층은 비었는지 보지 않으므로, 이 래퍼가 없으면 {@code 200 {"keys":[]}} 가 정상값으로 캐시에
 * 들어앉는다. <b>키가 있는 응답은 그대로 통과</b>해야 하므로 둘을 함께 확인한다 — "전부 거절" 로 통과하는 것이
 * 아님을 보이지 않으면 이 테스트가 래퍼의 존재만 확인하는 셈이 된다.
 */
class NonEmptyJwkSetSourceTest {

    /** 몇 번 불렸는지 세고, 정해진 횟수만큼 빈 묶음을 돌려주는 소스. */
    private static final class CountingSource implements JWKSetSource<SecurityContext> {

        private final AtomicInteger calls = new AtomicInteger();
        private final JWKSet nonEmpty;
        private final int emptyTimes;

        private CountingSource(JWKSet nonEmpty, int emptyTimes) {
            this.nonEmpty = nonEmpty;
            this.emptyTimes = emptyTimes;
        }

        @Override
        public JWKSet getJWKSet(JWKSetCacheRefreshEvaluator evaluator, long currentTime, SecurityContext context) {
            return calls.incrementAndGet() <= emptyTimes ? new JWKSet(List.of()) : nonEmpty;
        }

        @Override
        public void close() {}
    }

    private static JWKSet oneKey() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("kid-1").generate();
        return new JWKSet(List.of(key.toPublicJWK()));
    }

    private static JWKSet fetch(JWKSetSource<SecurityContext> source) throws KeySourceException {
        return source.getJWKSet(JWKSetCacheRefreshEvaluator.noRefresh(), System.currentTimeMillis(), null);
    }

    @Test
    void 공개키가_없는_응답은_실패로_돌린다() throws Exception {
        JWKSetSource<SecurityContext> source = new NonEmptyJwkSetSource<>(new CountingSource(oneKey(), 1));

        assertThrows(KeySourceException.class, () -> fetch(source));
    }

    @Test
    void 빈_응답_뒤에_온_정상_JWKS_는_받는다() throws Exception {
        // 빈 응답이 캐시를 차지했다면 이 두 번째 조회가 여전히 빈 묶음을 만난다.
        CountingSource upstream = new CountingSource(oneKey(), 1);
        JWKSetSource<SecurityContext> source = new NonEmptyJwkSetSource<>(upstream);
        assertThrows(KeySourceException.class, () -> fetch(source));

        assertEquals(1, fetch(source).size());
        assertEquals(2, upstream.calls.get(), "두 번째 조회가 실제로 provider 까지 갔어야 한다");
    }

    @Test
    void 공개키가_있으면_그대로_통과한다() throws Exception {
        JWKSetSource<SecurityContext> source = new NonEmptyJwkSetSource<>(new CountingSource(oneKey(), 0));

        assertEquals(1, fetch(source).size());
    }
}

package com.offway.core.common.external;

/**
 * 이 연동의 캐시를 지금 써도 되나(#403).
 *
 * <p>캐시를 가진 서비스가 <b>설정 서비스 전체가 아니라 이 물음에만</b> 의존하게 한다. 그래야 단위
 * 테스트가 저장소·스케줄러를 끌고 오지 않고 람다 하나로 끝나고, 캐시 소유자가 설정 저장 경로를
 * 실수로 부를 일도 없다.
 *
 * <p>구현은 {@link ExternalApiSettings} 다.
 */
@FunctionalInterface
public interface ExternalApiCachePolicy {

    boolean cacheEnabled(ExternalApi api);

    /**
     * 늘 캐시를 쓴다 — <b>이 기능이 붙기 전의 동작</b>.
     *
     * <p>설정과 무관한 자리(단위 테스트·E2E)를 위한 것이다. 운영 빈은 {@link ExternalApiSettings} 다.
     */
    ExternalApiCachePolicy ALWAYS_CACHE = api -> true;
}

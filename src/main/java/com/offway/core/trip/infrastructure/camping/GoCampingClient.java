package com.offway.core.trip.infrastructure.camping;

import com.offway.core.trip.infrastructure.camping.dto.GoCampsiteResult;
import java.time.Duration;

/**
 * 고캠핑 야영장 조회 port(#510). 도메인·서비스는 이 인터페이스에만 의존한다.
 */
public interface GoCampingClient {

    /**
     * 전국 야영장을 <b>한 번에</b> 받는다.
     *
     * <p>실측 3,115건 · 7.3MB · 3.1초다. 페이지를 나누지 않는 이유는 {@link GoCampsiteResult} 에 적었다.
     *
     * @param maxWait 이 조회 전체의 시간 상한
     * @return 쓸 수 있는 야영장(휴장·좌표 없음 제외). 키가 없으면 빈 결과
     */
    GoCampsiteResult findAll(Duration maxWait);
}

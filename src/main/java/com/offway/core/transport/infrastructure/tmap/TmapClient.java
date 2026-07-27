package com.offway.core.transport.infrastructure.tmap;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import java.util.Optional;

/**
 * TMAP(SK 오픈API) 경로 조회 port. 자동차 실측 소요시간을 준다.
 *
 * <p>키가 없거나 호출이 실패하면 <b>빈 Optional</b> 을 돌려준다 — 이동시간은 코스의 보조 정보라 502 로 올리기보다 직선거리 근사로
 * 폴백하는 게 낫다(graceful degradation). 결과는 약관상 24시간 이상 저장하지 않는다(영구 캐시 금지).
 */
public interface TmapClient {

    /** 출발→목적지 자동차 실측 경로(소요시간·거리). 키 없음·실패 시 빈 Optional. */
    Optional<TmapRoute> carRoute(Coordinate origin, Coordinate destination);
}

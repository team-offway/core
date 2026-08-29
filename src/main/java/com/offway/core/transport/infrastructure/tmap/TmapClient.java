package com.offway.core.transport.infrastructure.tmap;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.dto.CarRouteResult;
import java.util.List;
import java.util.Optional;

/**
 * TMAP(SK 오픈API) 경로 조회 port. 자동차 실측 소요시간을 준다.
 *
 * <p>키가 없거나 호출이 실패하면 <b>빈 Optional</b> 을 돌려준다 — 이동시간은 코스의 보조 정보라 502 로 올리기보다 직선거리 근사로
 * 폴백하는 게 낫다(graceful degradation). 결과는 약관상 24시간 이상 저장하지 않는다(영구 캐시 금지).
 */
public interface TmapClient {

    /**
     * 출발→목적지 자동차 실측 경로(소요시간·거리).
     *
     * <p>실패는 <b>사유별로 갈라서</b> 돌려준다({@link CarRouteResult}). 좌표가 도로에 안 붙는 것과 타임아웃은
     * 상위가 해야 할 일이 다르다 — 앞은 기억해서 다음 코스에서 빼고, 뒤는 그냥 폴백한다(#335).
     */
    CarRouteResult carRoute(Coordinate origin, Coordinate destination);

    /**
     * 자동차 경유지 순서 최적화(routeOptimization10) — 방문 지점들을 실도로 기준 최단 동선으로 재정렬한다. 첫 지점을 출발,
     * 마지막 지점을 도착으로 고정하고 사이(경유지)를 최적 순서로 배치한다.
     *
     * @param points 방문 지점(첫=출발·마지막=도착·사이=경유지). 총 3~12곳(경유지 최대 10)일 때만 유효.
     * @return 입력 인덱스를 방문 순서대로 나열한 리스트. 키 없음·범위 밖·실패 시 빈 Optional(폴백 유도).
     */
    Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points);
}

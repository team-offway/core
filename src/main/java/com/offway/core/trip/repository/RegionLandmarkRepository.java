package com.offway.core.trip.repository;

import java.util.List;
import java.util.Map;

/**
 * 지역의 <b>대표 볼거리 이름</b> 조회 port(#140) — 지역 한 줄 소개의 재료.
 *
 * <p><b>지어내지 않는다.</b> 소개 문구를 사람이 쓰면 89곳 카피를 만들어야 하고, 그건 실재하는 지역에 대한
 * 주장이라 틀리면 그대로 사용자에게 나간다. 대신 우리가 이미 가진 사실 — 그 지역에 실제로 있는 국가유산과
 * 볼거리 이름 — 을 조합한다.
 *
 * <p><b>port 로 두는 이유는 읽는 쪽이 다른 도메인이기 때문이다.</b> 장소 데이터는 {@code trip} 이 가지고
 * 있지만 소개를 만드는 것은 {@code region} 이다(#249 에서 가른 기준). 도메인 경계를 넘는 자리는 SQL 이
 * 아니라 계약에 기대야 한다.
 */
public interface RegionLandmarkRepository {

    /** 지역별 대표 볼거리 이름 — 지역당 최대 {@code limit} 개. 재료가 없는 지역은 키가 없다. */
    Map<Long, List<String>> topHeritageNames(int limit);

    /**
     * 국가유산이 없는 지역의 대체 — 관광 콘텐츠성이 높은 인허가 볼거리(전통사찰·박물관 등).
     *
     * <p>실제로 한 곳(대구 서구)이 여기 해당한다. 그 지역의 국가유산이 무형유산뿐이라 방문 대상이 없다.
     */
    Map<Long, List<String>> topLicensedSightNames(int limit);
}

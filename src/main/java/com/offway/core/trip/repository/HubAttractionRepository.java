package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HubAttraction;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** 중심 관광지 영속 port. 구현은 {@link HubAttractionRepositoryImpl}. */
public interface HubAttractionRepository {

    /** 한 지역의 중심 관광지(순위 오름차순). */
    List<HubAttraction> findByRegionId(Long regionId);

    /** 여러 지역을 한 번에 — 목록 화면이 지역마다 묻지 않게(N+1 방지). */
    List<HubAttraction> findByRegionIds(List<Long> regionIds);

    /**
     * 한 지역의 중심 관광지를 통째로 <b>교체</b>한다.
     *
     * <p>부분 갱신하지 않는다 — 순위는 달마다 새로 매겨지므로, 옛 달의 항목이 남으면 순위가 중복되거나
     * 사라진 곳이 계속 보인다.
     */
    /** 목표월(또는 그 이후) 자료를 이미 가진 지역 id — 이 지역은 외부를 부르지 않는다(#337). */
    Set<Long> regionIdsFreshFrom(YearMonth baseYm);

    void replaceRegion(Long regionId, List<HubAttraction> attractions);

    long count();
}

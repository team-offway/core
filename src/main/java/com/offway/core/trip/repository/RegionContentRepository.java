package com.offway.core.trip.repository;

import com.offway.core.trip.domain.StoredRegionContent;
import java.util.List;

/** 지역 콘텐츠 영속 port(#193). 구현은 {@link RegionContentRepositoryImpl}. */
public interface RegionContentRepository {

    /** 여러 지역을 한 번에 — 목록 화면이 지역마다 묻지 않게(N+1 방지). */
    List<StoredRegionContent> findByRegionIds(List<Long> regionIds);

    /**
     * 전량을 <b>교체</b>한다.
     *
     * <p>갱신은 89곳을 통째로 다시 받는 값이라 부분 갱신하지 않는다 — 고시 개정으로 빠진 지역이 남으면
     * 없는 지역의 콘텐츠가 계속 보인다.
     */
    void replaceAll(List<StoredRegionContent> contents);

    /** 저장된 지역 수 — 적재가 돌았는지 판단하는 데 쓴다. */
    long count();
}

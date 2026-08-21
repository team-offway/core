package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionPoi;
import java.time.YearMonth;
import java.util.List;

/** 지역 장소 풀 영속 port(#304). 구현은 {@link RegionPoiRepositoryImpl}. */
public interface RegionPoiRepository {

    /**
     * 이 지역의 사진 있는 장소를 최대 {@code limit} 개.
     *
     * <p>상한을 받는 이유는 화면이 정한 수(매력 포인트 장소 최대 10개)를 넘겨 읽을 이유가 없어서다.
     */
    List<RegionPoi> findShowable(long regionId, int limit);

    /** 그 달치가 이미 적재됐는가 — 갱신 배치가 외부를 부를지 가른다. */
    boolean hasFresh(long regionId, YearMonth baseYm);

    /** 이 지역의 장소를 통째로 갈아 끼운다. 지우고 넣는 것이 한 트랜잭션이어야 중간 상태가 안 보인다. */
    void replaceRegion(long regionId, List<RegionPoi> pois);
}
